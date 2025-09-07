package com.example.groviq.frontEnd.bottomBars.audioBottomBar

import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.subscribeMe
import kotlinx.coroutines.isActive

@OptIn(
    UnstableApi::class
)
@Composable
fun closedBar(mainViewModel : PlayerViewModel, onToogleSheet: () -> Unit, searchViewModel: SearchViewModel, songProgressUi: State<CurrentSongTimeProgress>)
{
    val baseColor   = Color(30, 30, 30)
    val bottomColor = Color(30, 30, 30)

    val highlightColor = Color(200, 100, 100, 255)

    val gradientShift = remember { androidx.compose.animation.core.Animatable(0f) }
    val gradientAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

    // current reactive variables for subscribe //
    val currentStatus       by mainViewModel.uiState.subscribeMe { it.currentStatus  }
    val playingHash         by mainViewModel.uiState.subscribeMe { it.playingHash    }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }

    val loadingAudio = (currentStatus == playerStatus.IDLE || currentStatus == playerStatus.BUFFERING)

    LaunchedEffect(loadingAudio) {
        if (loadingAudio) {
            gradientAlpha.animateTo(1f, animationSpec = tween(500))
            while (isActive && loadingAudio) {
                gradientShift.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
        } else {
            gradientAlpha.animateTo(0f, animationSpec = tween(500, easing = FastOutSlowInEasing))
            gradientShift.snapTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize())
    {
        BoxWithConstraints(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 15.dp, end = 15.dp)
                .fillMaxWidth()
                .height(80.dp)
                .background(bottomColor)
        ) {

            val boxWidth = constraints.maxWidth.toFloat()
            val gradientWidth = boxWidth
            val alphaFactor = 0.5f

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 1f - gradientAlpha.value * alphaFactor),
                                highlightColor.copy(alpha = gradientAlpha.value * alphaFactor),
                                baseColor.copy(alpha = 1f - gradientAlpha.value * alphaFactor)
                            ),
                            startX  = gradientShift.value * (boxWidth + gradientWidth) - gradientWidth,
                            endX    = gradientShift.value * (boxWidth + gradientWidth),
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onToogleSheet() }
            ){

                LinearProgressIndicator(
                    progress = songProgressUi.value.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(onClick = {
                            AppViewModels.player.playerManager.prevSong(mainViewModel, searchViewModel = searchViewModel )
                        },
                    ) {
                        Icon(imageVector =
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "SkipPrev",
                            tint = Color(255, 255, 255)
                        )
                    }

                    if (loadingAudio)
                    {
                        CircularProgressIndicator()
                    }
                    else
                    {
                        IconButton(onClick = {
                                if (currentStatus == playerStatus.PAUSE)
                                    AppViewModels.player.playerManager.resume()
                                if (currentStatus == playerStatus.PLAYING)
                                    AppViewModels.player.playerManager.pause()
                            },
                        ) {
                            Icon(
                                imageVector = if (currentStatus == playerStatus.PAUSE)
                                    Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                ,
                                contentDescription = "Pause/Play",
                                tint = Color(255, 255, 255)
                            )
                        }
                    }


                    IconButton(onClick = {
                            AppViewModels.player.playerManager.nextSong(mainViewModel, searchViewModel)
                        },
                    ) {
                        Icon(
                            imageVector =
                            Icons.Rounded.SkipNext,
                            contentDescription = "SkipNext",
                            tint = Color(255, 255, 255)
                        )
                    }

                    Column()
                    {
                        val song = allAudioData[playingHash]

                        Text(song!!.title, color = Color(255, 255, 255))

                        Row()
                        {
                            song.artists.forEach { artist ->

                                Text(
                                    artist.title + if (artist != song.artists.last()) ", " else "",
                                    maxLines = 1, color = Color(255, 255, 255)
                                )

                            }
                        }
                    }

                }

            }

        }
    }

}