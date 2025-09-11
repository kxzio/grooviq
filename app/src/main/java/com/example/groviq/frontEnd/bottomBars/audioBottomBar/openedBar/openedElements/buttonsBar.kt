package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.playerInputHandlers

@OptIn(
    UnstableApi::class
)
@Composable
fun bottomBarUI.elements.openedElements.activityButtons(songProgressUi: State<CurrentSongTimeProgress>, currentStatus: playerStatus)
{

    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {

        IconButton(
            onClick =
            {
                playerInputHandlers.processInputBackwards(songProgressUi.value.progress)
            },
            modifier = Modifier
                .size(64.dp)
                .background(color = Color(255, 255, 255, 0), shape = CircleShape)
        ) {
            Icon(
                imageVector =
                Icons.Rounded.SkipPrevious
                ,
                contentDescription = "SkipPrev",
                modifier = Modifier.size(32.dp),
                tint = Color(255, 255, 255)
            )
        }

        Spacer(
            Modifier.width(16.dp))


        IconButton(
            onClick =
            {
                if (currentStatus == playerStatus.PAUSE)
                    AppViewModels.player.playerManager.resume()
                if (currentStatus == playerStatus.PLAYING)
                    AppViewModels.player.playerManager.pause()
            },
            modifier = Modifier
                .size(125.dp)
                .background(color = Color(255, 255, 255, 0), shape = CircleShape)
        ) {
            Icon(
                imageVector = if (currentStatus == playerStatus.PAUSE)
                    Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                ,
                contentDescription = "Pause/Play",
                modifier = Modifier.size(60.dp),
                tint = Color(255, 255, 255)
            )
        }

        Spacer(
            Modifier.width(16.dp))

        IconButton(
            onClick =
            {
                playerInputHandlers.processInputForwards()
            },
            modifier = Modifier
                .size(64.dp)
                .background(color = Color(255, 255, 255, 0), shape = CircleShape)

        ) {
            Icon(
                imageVector =
                Icons.Rounded.SkipNext
                ,
                contentDescription = "SkipNext",
                modifier = Modifier.size(32.dp),
                tint = Color(255, 255, 255)
            )
        }


    }

}