package com.example.groviq.backEnd.streamProcessor

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.streamProcessor.innerTubeMine.getBestAudioStreamUrl
import com.example.groviq.getPythonModule
import com.example.groviq.globalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

var currentFetchJob: Job? = null

fun fetchAudioStream(mainViewModel: PlayerViewModel, songKey: String) {
    val song = mainViewModel.uiState.value.allAudioData[songKey] ?: return

    //if it already in handle - exit
    if (song.progressStatus.streamHandled) return

    //flag that we handlded by this thread
    mainViewModel.updateStatusForSong(
        songKey,
        song.progressStatus.copy(streamHandled = true)
    )

    //cancel the old one
    currentFetchJob?.cancel()

    currentFetchJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            val audioUrl = getBestAudioStreamUrl(song.link)

            if (!isActive) return@launch

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
            withContext(Dispatchers.Main) {
                val latestSong = mainViewModel.uiState.value.allAudioData[songKey] ?: return@withContext
                mainViewModel.updateStatusForSong(
                    songKey,
                    latestSong.progressStatus.copy(streamHandled = false)
                )
            }
        }
    }
}


private var currentArtistJob: Job? = null

fun getStreamForAudio(link: String, onResult: (String?) -> Unit) {
    currentArtistJob?.cancel()
    currentArtistJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            val streamUrl = getPythonModule(globalContext!!)
                .callAttr("getStream", link)
                .toString()

            withContext(Dispatchers.Main) {
                onResult(streamUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onResult(null)
            }
        }
    }
}