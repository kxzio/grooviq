package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.formatTime
import com.example.groviq.frontEnd.grooviqUI

@androidx.annotation.OptIn(
    UnstableApi::class
)
@kotlin.OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun grooviqUI.elements.openedElements.sliderWithDigits(song : songData, songProgressUi: State<CurrentSongTimeProgress>,)
{
    Column(modifier = Modifier.fillMaxWidth())
    {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        {
            Box(modifier = Modifier.fillMaxWidth())
            {

                Slider(
                    enabled = AppViewModels.player.playerManager.player.duration != C.TIME_UNSET,
                    value = songProgressUi.value.progress,
                    onValueChange = { newProgress ->
                        val duration = AppViewModels.player.playerManager.player!!.duration
                        val newPosition = (duration * newProgress).toLong()
                        setSongProgress(newProgress, newPosition)
                        AppViewModels.player.playerManager.player!!.seekTo(newPosition)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    thumb = {}
                )

            }
        }

        Spacer(
            Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 25.dp))
        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(songProgressUi.value.position), color = Color(255, 255, 255, 150))
                Text(text = formatTime(song?.duration ?: 0L), color = Color(255, 255, 255, 150))
            }
        }


    }
}