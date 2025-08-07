package com.example.groviq.backEnd.playEngine

import android.os.Looper
import androidx.media3.common.Player
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.playerManager
import java.util.logging.Handler

var listenersAttached = false

fun createListeners(searchViewModel : SearchViewModel, //search view
                    mainViewModel   : PlayerViewModel, //player view
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
                val position = playerManager.player.currentPosition
                val duration = playerManager.player.duration

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

    playerManager.player.addListener(object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    mainViewModel.setPlayerStatus(playerStatus.BUFFERING)
                }
                Player.STATE_READY -> {
                    if (playerManager.player.playWhenReady) {
                        mainViewModel.setPlayerStatus(playerStatus.PLAYING)
                    } else {
                        mainViewModel.setPlayerStatus(playerStatus.PAUSE)
                    }
                }
                Player.STATE_ENDED -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)

                    playerManager.nextSong(mainViewModel)

                }
                Player.STATE_IDLE -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && playerManager.player.playbackState == Player.STATE_READY) {
                mainViewModel.setPlayerStatus(playerStatus.PLAYING)
            } else if (!playWhenReady && playerManager.player.playbackState == Player.STATE_READY) {
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
    })

    listenersAttached = true
}