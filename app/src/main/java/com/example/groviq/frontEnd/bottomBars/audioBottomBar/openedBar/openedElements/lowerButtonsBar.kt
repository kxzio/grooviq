package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.frontEnd.bottomBars.openTrackSettingsBottomBar
import com.example.groviq.frontEnd.grooviqUI

@OptIn(
    UnstableApi::class
)
@Composable
fun grooviqUI.elements.openedElements.lowerActivityButtons(
    mainViewModel: PlayerViewModel,
    song : songData,
    isShuffle : Boolean,
    repeatMode: repeatMods,

)
{
    Row( Modifier.fillMaxWidth().padding(horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick =
            {
                mainViewModel.toogleShuffleMode()
            },
        ) {
            Icon(
                imageVector =
                Icons.Rounded.Shuffle,
                contentDescription = "SkipPrev",
                tint = Color(255, 255, 255, if (isShuffle) 255 else 100)
            )
        }

        IconButton(
            onClick =
            {
                mainViewModel.toogleRepeatMode()
            },
        ) {
            Icon(
                imageVector =
                if (repeatMode == repeatMods.REPEAT_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "SkipPrev",
                tint = Color(255, 255, 255,
                    if (repeatMode == repeatMods.NO_REPEAT)       100
                    else if (repeatMode == repeatMods.REPEAT_ALL) 255
                    else 255)
            )
        }

        val liked by mainViewModel.isAudioSourceContainsSong(song!!.link, "Favourite")
            .collectAsState(initial = false)

        IconButton(
            onClick =
            {
                if (liked)
                {
                    mainViewModel.removeSongFromAudioSource(song!!.link, "Favourite")
                    mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[song!!.link]!!)
                    mainViewModel.saveAudioSourcesToRoom()
                }
                else
                {
                    mainViewModel.addSongToAudioSource(song!!.link, "Favourite")
                    mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[song!!.link]!!)
                    mainViewModel.saveAudioSourcesToRoom()
                }
            },
        ) {
            Icon(
                imageVector =
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                ,
                contentDescription = "like",
                tint = Color(255, 255, 255)
            )
        }

        IconButton(
            onClick =
            {
                openTrackSettingsBottomBar(song?.link ?: "")
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert
                ,
                contentDescription = "more",
                tint = Color(255, 255, 255)
            )
        }
    }
}