package com.example.groviq.frontEnd.bottomBars.audioBottomBar

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.setSongProgress


object playerInputHandlers {

    @OptIn(UnstableApi::class)
    fun processInputBackwards(songProgress : Float)
    {
        if (songProgress > 0.2f)
        {
            setSongProgress(0f, 0L)
            AppViewModels.player.playerManager.player!!.seekTo(0L)
        }
        else
            AppViewModels.player.playerManager.prevSong(AppViewModels.player, AppViewModels.search)
    }

    @OptIn(UnstableApi::class)
    fun processInputForwards()
    {
        AppViewModels.player.playerManager.nextSong(AppViewModels.player, AppViewModels.search)
    }
}


