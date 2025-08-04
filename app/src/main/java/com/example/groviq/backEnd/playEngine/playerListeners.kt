package com.example.groviq.backEnd.playEngine

import androidx.media3.common.Player
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.playerManager

var listenersAttached = false

fun createListeners(searchViewModel : SearchViewModel, //search view
                    mainViewModel   : PlayerViewModel, //player view
)
{
    if (listenersAttached == true)
        return

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
    })

    listenersAttached = true
}