package com.example.groviq.frontEnd.bottomBars.audioBottomBar.closedBar

import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.closedBar.closedElements.gradientLoaderBar
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.subscribeMe
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.isActive

@OptIn(
    UnstableApi::class
)
@Composable
fun closedBar(mainViewModel : PlayerViewModel, onToogleSheet: () -> Unit, searchViewModel: SearchViewModel, songProgressUi: State<CurrentSongTimeProgress>, hazeState: HazeState)
{
    val baseColor   = Color(20, 20, 20, 140)

    val gradientShift = remember { androidx.compose.animation.core.Animatable(0f) }
    val gradientAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

    // current reactive variables for subscribe //
    val currentStatus       by mainViewModel.uiState.subscribeMe { it.currentStatus     }
    val playingHash         by mainViewModel.uiState.subscribeMe { it.playingHash       }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData      }
    val isPlaying           by mainViewModel.uiState.subscribeMe { it.isPlaying         }


    val loadingAudio = (currentStatus == playerStatus.IDLE || currentStatus == playerStatus.BUFFERING)

    //gradient start animating
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
                .padding(bottom = 100.dp, start = 25.dp, end = 25.dp)
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(255, 255, 255, 28), RoundedCornerShape(8.dp))
                .background(baseColor)
                .hazeEffect(state = hazeState)

        ) {

            grooviqUI.elements.closedElements.gradientLoaderBar(constraints, baseColor,
                gradientShift = gradientShift.value,
                gradientAlpha = gradientAlpha.value
            )

            Box(Modifier.align(Alignment.TopStart))
            {
                LinearProgressIndicator(
                    progress = songProgressUi.value.progress,
                    modifier = Modifier
                        .fillMaxWidth().height(4.dp).padding(1.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0, 0, 0, 0)
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onToogleSheet() }
            ){

                Row(
                    modifier = Modifier
                        .fillMaxSize().padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(onClick = {
                        if (AppViewModels.player.playerManager.player.playWhenReady == false)
                            AppViewModels.player.playerManager.resume()
                        else
                            AppViewModels.player.playerManager.pause()
                    },
                    ) {
                        Icon(
                            imageVector = if (isPlaying == false)
                                Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                            ,
                            contentDescription = "Pause/Play",
                            tint = Color(255, 255, 255),
                            modifier = Modifier.size(30.dp)
                        )
                    }


                    /*
                                        IconButton(onClick = {
                        AppViewModels.player.playerManager.nextSong(mainViewModel, searchViewModel)
                    },
                    ) {
                        Icon(
                            imageVector =
                            Icons.Rounded.SkipNext,
                            contentDescription = "SkipNext",
                            tint = Color(255, 255, 255),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                     */


                    Column(Modifier.fillMaxWidth().padding(start = 15.dp))
                    {
                        val song = allAudioData[playingHash]

                        Text(text = song!!.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, color = Color(255, 255, 255),
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                repeatDelayMillis = 2000,
                                velocity = 40.dp
                            )
                        )

                        Spacer(Modifier.height(4.dp))

                        Row()
                        {
                            song.artists.forEach { artist ->

                                Text(
                                    artist.title + if (artist != song.artists.last()) ", " else "",
                                    maxLines = 1, fontSize = 15.sp, color = Color(255, 255, 255, 100),
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                        repeatDelayMillis = 2000,
                                        velocity = 40.dp
                                    ),
                                )

                            }
                        }
                    }

                }

            }

        }

    }

}