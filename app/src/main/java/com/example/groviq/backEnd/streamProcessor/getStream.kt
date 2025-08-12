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
            withContext(Dispatchers.Main) {
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


private var currentArtistJob: Job? = null



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