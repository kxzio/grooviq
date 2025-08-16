package com.example.groviq.backEnd.playEngine

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.playerManager
import com.example.groviq.service.pendingDirection
import com.example.groviq.service.songPendingIntentNavigationDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.logging.Handler

var listenersAttached = false

@OptIn(
    UnstableApi::class
)
fun createListeners(
    searchViewModel: SearchViewModel, //search view
    mainViewModel: PlayerViewModel, //player view
)
{
    if (listenersAttached == true)
        return

    //for progress handling
    val progressHandler =
        android.os.Handler(
            Looper.getMainLooper()
        )
    var progressRunnable: Runnable? = null

    fun startProgressHandler() {
        progressRunnable = object : Runnable {
            override fun run() {
                val position = playerManager.player!!.currentPosition
                val duration = playerManager.player!!.duration

                if (duration > 0) {
                    val progressFraction = position.toFloat() / duration
                    setSongProgress(progressFraction, position)
                }

                progressHandler.postDelayed(this, 500)
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    fun stopProgressHandler() {
        progressRunnable?.let {
            progressHandler.removeCallbacks(it)
        }
    }

    playerManager.player!!.addListener(object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    mainViewModel.setPlayerStatus(playerStatus.BUFFERING)
                }
                Player.STATE_READY -> {
                    if (playerManager.player!!.playWhenReady) {
                        mainViewModel.setPlayerStatus(playerStatus.PLAYING)

                        val ui = mainViewModel.uiState.value
                        if (ui.allAudioData[ui.playingHash]!!.duration == 0L)
                        {
                            mainViewModel.updateDurationForSong(ui.playingHash, playerManager.player!!.duration)
                        }

                    } else {
                        mainViewModel.setPlayerStatus(playerStatus.PAUSE)
                    }
                }
                Player.STATE_ENDED -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)

                    playerManager.nextSong(mainViewModel, searchViewModel)

                }
                Player.STATE_IDLE -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && playerManager.player!!.playbackState == Player.STATE_READY) {
                mainViewModel.setPlayerStatus(playerStatus.PLAYING)
            } else if (!playWhenReady && playerManager.player!!.playbackState == Player.STATE_READY) {
                mainViewModel.setPlayerStatus(playerStatus.PAUSE)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressHandler()
            } else {
                stopProgressHandler()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Текущий трек завершён
                mainViewModel.setPlayerStatus(playerStatus.IDLE)
                playerManager.nextSong(mainViewModel, searchViewModel)
            }
        }

    })

    val scope = CoroutineScope(
        Dispatchers.Main)

    scope.launch {
        snapshotFlow { songPendingIntentNavigationDirection.value }
            .collect { direction ->
                when(direction) {
                    pendingDirection.TO_NEXT_SONG -> {
                        playerManager.nextSong(mainViewModel, searchViewModel)
                        songPendingIntentNavigationDirection.value = pendingDirection.EMPTY
                    }
                    pendingDirection.TO_PREVIOUS_SONG -> {
                        playerManager.prevSong(mainViewModel, searchViewModel)
                        songPendingIntentNavigationDirection.value = pendingDirection.EMPTY
                    }
                    pendingDirection.EMPTY -> {

                    }
                }
            }
    }

    listenersAttached = true
}