package com.example.groviq.backEnd.streamProcessor

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.streamProcessor.innerTubeMine.getBestAudioStreamUrl
import com.example.groviq.getPythonModule
import com.example.groviq.globalContext
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

var currentFetchJob: Job? = null

fun fetchAudioStream(mainViewModel: PlayerViewModel, songKey: String) {

    val song = mainViewModel.uiState.value.allAudioData[songKey] ?: return

    // Skip if already being handled
    if (song.progressStatus.streamHandled) return

    // Mark as handled
    mainViewModel.updateStatusForSong(
        songKey,
        song.progressStatus.copy(streamHandled = true)
    )

    // Cancel previous job
    currentFetchJob?.cancel()

    currentFetchJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            val audioUrl = getBestAudioStreamUrl(song.link)

            if (!isActive) return@launch // if coroutine was cancelled, stop here

            if (audioUrl != null) {
                withContext(Dispatchers.Main) {
                    val latestSong = mainViewModel.uiState.value.allAudioData[songKey] ?: return@withContext
                    mainViewModel.updateStreamForSong(songKey, audioUrl)
                }
            } else {
                println("No valid audio stream found for $songKey")
            }

        } catch (e: Exception) {
            println("Error fetching stream for $songKey: ${e.message}")

        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                // Always reset streamHandled, even on cancel
                val latestSong = mainViewModel.uiState.value.allAudioData[songKey] ?: return@withContext
                if (latestSong.progressStatus.streamHandled) {
                    mainViewModel.updateStatusForSong(
                        songKey,
                        latestSong.progressStatus.copy(streamHandled = false)
                    )
                }
            }
        }
    }
}

var currentFetchQueueJob: Job? = null

fun fetchQueueStream(mainViewModel: PlayerViewModel) {
    // Cancel previous job
    currentFetchQueueJob?.cancel()

    val currentQueue = mainViewModel.uiState.value.currentQueue

    if (currentQueue.isEmpty())
        return

    val currentPos = mainViewModel.uiState.value.posInQueue

    val fromIndex = (currentPos - 3).coerceAtLeast(0)
    val toIndex = (currentPos + 3).coerceAtMost(currentQueue.size)

    currentFetchQueueJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            coroutineScope {
                currentQueue.mapNotNull { it.hashKey }
                    .mapNotNull { key -> mainViewModel.uiState.value.allAudioData[key] }
                    .subList(fromIndex, toIndex)
                    .map { song ->
                        async {
                            // Skip if already being handled
                            if (song.progressStatus.streamHandled) return@async

                            if (song.shouldGetStream().not()) return@async

                            // Mark as handled (на Main)
                            withContext(Dispatchers.Main) {
                                mainViewModel.updateStatusForSong(
                                    song.link,
                                    song.progressStatus.copy(streamHandled = true)
                                )
                            }

                            try {
                                val audioUrl = getBestAudioStreamUrl(song.link)

                                if (audioUrl != null) {
                                    withContext(Dispatchers.Main) {
                                        val latestSong =
                                            mainViewModel.uiState.value.allAudioData[song.link]
                                                ?: return@withContext
                                        mainViewModel.updateStreamForSong(song.link, audioUrl)
                                    }
                                }
                            } catch (e: Exception) {

                            } finally {
                                withContext(NonCancellable + Dispatchers.Main) {
                                    val latestSong =
                                        mainViewModel.uiState.value.allAudioData[song.link]
                                            ?: return@withContext
                                    if (latestSong.progressStatus.streamHandled) {
                                        mainViewModel.updateStatusForSong(
                                            song.link,
                                            latestSong.progressStatus.copy(streamHandled = false)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
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

var currentFetchImageJob: Job? = null

fun fetchNewImage(mainViewModel: PlayerViewModel, songKey: String) {
    val song = mainViewModel.uiState.value.allAudioData[songKey] ?: return

    // Cancel previous job
    currentFetchImageJob?.cancel()

    currentFetchImageJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            val audioImage = getPythonModule(globalContext!!).callAttr("getTrackImage", songKey).toString()

            if (!isActive) return@launch // if coroutine was cancelled, stop here

            if (audioImage != null) {
                val trackBitmap = loadBitmapFromUrl(audioImage)

                withContext(Dispatchers.Main) {
                    mainViewModel.updateImageForSong(songKey, trackBitmap!!)
                }
            } else {
                println("No valid image found for $songKey")
            }

        } catch (e: Exception) {
            println("Error fetching image for $songKey: ${e.message}")

        } finally {
            withContext(Dispatchers.Main) {

            }
        }
    }
}