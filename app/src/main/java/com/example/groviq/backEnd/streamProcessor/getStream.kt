package com.example.groviq.backEnd.streamProcessor

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.streamProcessor.innerTubeMine.getBestAudioStreamUrl
import com.example.groviq.getPythonModule
import com.example.groviq.globalContext
import com.example.groviq.hasInternetConnection
import com.example.groviq.loadBitmapFromUrl
import com.example.groviq.retryWithBackoff
import com.example.groviq.waitForInternet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException


//global fetch jobs
private val fetchJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()
private val fetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun fetchAudioStream(mainViewModel: PlayerViewModel, songKey: String) {

    //if we already have this job - cancel
    if (fetchJobs.containsKey(songKey)) return

    val songSnapshot = mainViewModel.uiState.value.allAudioData[songKey] ?: return

    //if already in process - exit
    if (songSnapshot.progressStatus.streamHandled) return

    //flag song, that we already processing song
    runOnMain {
        mainViewModel.updateStatusForSong(songKey, songSnapshot.progressStatus.copy(streamHandled = true))
    }

    //create a job and map it to delete it
    val job = fetchScope.launch {
        try {
            // if we have to internet, wait 60s and then retry
            if (!hasInternetConnection(globalContext!!)) {
                //dont fall, wait 60s
                try {
                    waitForInternet(maxWaitMs = 60_000L, checkIntervalMs = 1_000L)
                } catch (e: IOException) {
                    //cancel - go to finaly and cancel flag
                    throw e
                }
            }

            //get best stream url with 3 retries if we have no internet
            val audioUrl = retryWithBackoff(retries = 3, initialDelayMs = 500L) {

                //safe check : if courinte was canceled - exit
                if (!isActive) throw CancellationException()

               //add timeout 30s to fetch song - maximum time
                withContext(Dispatchers.IO) {
                    val result = withTimeoutOrNull(30_000L) {
                        getBestAudioStreamUrl(songSnapshot.link)
                    }
                    result ?: throw IOException("Extractor timed out")
                }
            }

            if (!isActive) return@launch

            if (audioUrl != null) {
                //before writing, making sure songs exists
                withContext(Dispatchers.Main) {
                    val latestSong = mainViewModel.uiState.value.allAudioData[songKey] ?: return@withContext
                    //additional check - if other thread already set stream
                    if (latestSong.shouldGetStream().not() && latestSong.stream.streamUrl != null) {
                        // nah, we dont need it
                        return@withContext
                    }
                    mainViewModel.updateStreamForSong(songKey, audioUrl)
                }
            } else {
                //not found
                println("No audioUrl for $songKey")
            }

        } catch (ce: CancellationException) {
            //error - go to final and cancel flag
            throw ce
        } catch (e: Exception) {
            //extractor errors
            e.printStackTrace()
            runOnMain {

            }
        } finally {
            // always delete flag in NonCancellable + Main
            withContext(NonCancellable + Dispatchers.Main) {
                val latestSong = mainViewModel.uiState.value.allAudioData[songKey]
                if (latestSong != null && latestSong.progressStatus.streamHandled) {
                    mainViewModel.updateStatusForSong(songKey, latestSong.progressStatus.copy(streamHandled = false))
                }
            }
            //delete job from map
            fetchJobs.remove(songKey)
        }
    }

    //move job to map
    fetchJobs[songKey] = job
}

fun cancelFetchForSong(songKey: String, mainViewModel: PlayerViewModel) {
    val job = fetchJobs.remove(songKey)
    job?.cancel(CancellationException("Switching track"))

    CoroutineScope(Dispatchers.Main).launch {
        withContext(NonCancellable) {
            val latest = mainViewModel.uiState.value.allAudioData[songKey] ?: return@withContext
            if (latest.progressStatus.streamHandled) {
                mainViewModel.updateStatusForSong(songKey, latest.progressStatus.copy(streamHandled = false))
            }
        }
    }
}

var currentFetchQueueJob: Job? = null

fun fetchQueueStream(mainViewModel: PlayerViewModel) {
    CoroutineScope(Dispatchers.Main).launch {
        currentFetchQueueJob?.cancelAndJoin()

        val currentQueue = mainViewModel.uiState.value.currentQueue
        if (currentQueue.isEmpty()) return@launch

        val currentPos = mainViewModel.uiState.value.posInQueue
        val fromIndex = (currentPos - 3).coerceAtLeast(0)
        val toIndex = (currentPos + 3).coerceAtMost(currentQueue.size)

        currentFetchQueueJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                supervisorScope {
                    currentQueue.mapNotNull { it.hashKey }
                        .mapNotNull { key -> mainViewModel.uiState.value.allAudioData[key] }
                        .subList(fromIndex, toIndex)
                        .map { song ->
                            async {
                                if (!isActive || song.progressStatus.streamHandled || !song.shouldGetStream()) return@async

                                withContext(Dispatchers.Main) {
                                    mainViewModel.updateStatusForSong(
                                        song.link,
                                        song.progressStatus.copy(streamHandled = true)
                                    )
                                }

                                try {
                                    val audioUrl = getBestAudioStreamUrl(song.link)
                                    if (!isActive || audioUrl == null) return@async
                                    withContext(Dispatchers.Main) {
                                        mainViewModel.updateStreamForSong(song.link, audioUrl)
                                    }
                                } finally {
                                    withContext(NonCancellable + Dispatchers.Main) {
                                        val latest = mainViewModel.uiState.value.allAudioData[song.link] ?: return@withContext
                                        if (latest.progressStatus.streamHandled) {
                                            mainViewModel.updateStatusForSong(
                                                song.link,
                                                latest.progressStatus.copy(streamHandled = false)
                                            )
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    currentQueue.forEach { item ->
                        val song = mainViewModel.uiState.value.allAudioData[item.hashKey] ?: return@forEach
                        if (song.progressStatus.streamHandled) {
                            mainViewModel.updateStatusForSong(
                                song.link,
                                song.progressStatus.copy(streamHandled = false)
                            )
                        }
                    }
                }
            }
        }
    }
}

var currentAudioSourceFetch: Job? = null

fun fetchAudioSource(audioSource: String, mainViewModel: PlayerViewModel) {
    CoroutineScope(Dispatchers.Main).launch {
        if (mainViewModel.uiState.value.audioData[audioSource] == null) return@launch

        currentAudioSourceFetch?.cancelAndJoin()

        currentAudioSourceFetch = CoroutineScope(Dispatchers.IO).launch {
            val songs = mainViewModel.uiState.value.audioData[audioSource]
                ?.songIds
                ?.mapNotNull { mainViewModel.uiState.value.allAudioData[it] }
                ?: emptyList()

            try {
                supervisorScope {
                    songs.filter { !it.progressStatus.streamHandled && it.shouldGetStream() }
                        .chunked(5)
                        .forEach { batch ->
                            batch.map { song ->
                                async {
                                    if (!isActive) return@async

                                    withContext(Dispatchers.Main) {
                                        mainViewModel.updateStatusForSong(
                                            song.link,
                                            song.progressStatus.copy(streamHandled = true)
                                        )
                                    }

                                    try {
                                        val audioUrl = getBestAudioStreamUrl(song.link)
                                        if (!isActive || audioUrl == null) return@async
                                        withContext(Dispatchers.Main) {
                                            mainViewModel.updateStreamForSong(song.link, audioUrl)
                                        }
                                    } finally {
                                        withContext(NonCancellable + Dispatchers.Main) {
                                            val latest = mainViewModel.uiState.value.allAudioData[song.link] ?: return@withContext
                                            if (latest.progressStatus.streamHandled) {
                                                mainViewModel.updateStatusForSong(
                                                    song.link,
                                                    latest.progressStatus.copy(streamHandled = false)
                                                )
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    songs.forEach { song ->
                        val latest = mainViewModel.uiState.value.allAudioData[song.link] ?: return@forEach
                        if (latest.progressStatus.streamHandled) {
                            mainViewModel.updateStatusForSong(
                                song.link,
                                latest.progressStatus.copy(streamHandled = false)
                            )
                        }
                    }
                }
            }
        }
    }
}

var currentFetchImageJob: Job? = null

fun fetchNewImage(mainViewModel: PlayerViewModel, songKey: String) {
    val song = mainViewModel.uiState.value.allAudioData[songKey] ?: return

    CoroutineScope(Dispatchers.Main).launch {
        currentFetchImageJob?.cancelAndJoin()

        currentFetchImageJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioImage = getPythonModule(globalContext!!)
                    .callAttr("getTrackImage", songKey)
                    ?.toString()

                if (!isActive || audioImage.isNullOrEmpty()) return@launch

                val trackBitmap = loadBitmapFromUrl(audioImage) ?: return@launch
                if (!isActive) return@launch

                withContext(Dispatchers.Main) {
                    mainViewModel.updateImageForSong(songKey, trackBitmap)
                }
            } catch (e: Exception) {
                println("Error fetching image for $songKey: ${e.message}")
            }
        }
    }
}