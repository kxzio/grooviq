package com.example.groviq.frontEnd.bottomBars.audioBottomBar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.formatTime
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.background
import com.example.groviq.frontEnd.bottomBars.openTrackSettingsBottomBar
import com.example.groviq.frontEnd.subscribeMe
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun openedBar(mainViewModel : PlayerViewModel, onToogleSheet: () -> Unit, songProgressUi: State<CurrentSongTimeProgress>,
              searchViewModel: SearchViewModel, sheetState: SheetState)
{

    // current reactive variables for subscribe //
    val currentStatus       by mainViewModel.uiState.subscribeMe { it.currentStatus  }
    val playingHash         by mainViewModel.uiState.subscribeMe { it.playingHash    }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }
    val currentQueue        by mainViewModel.uiState.subscribeMe { it.currentQueue   }
    val posInQueue          by mainViewModel.uiState.subscribeMe { it.posInQueue     }
    val repeatMode          by mainViewModel.uiState.subscribeMe { it.repeatMode     }
    val songsLoader         by mainViewModel.uiState.subscribeMe { it.songsLoader    }
    val isShuffle           by mainViewModel.uiState.subscribeMe { it.isShuffle      }

    ModalBottomSheet(
        onDismissRequest = { onToogleSheet() },
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        dragHandle = null,
        containerColor = Color.Black,
        contentColor = Color.White,
        shape = RectangleShape
        //windowInsets = WindowInsets(0, 0, 0, 0)
    ) {

        val song = allAudioData[playingHash]

        Box(
            modifier = Modifier
                .fillMaxSize()
        )
        {
            if (song != null) {
                background(song)
            }

            Column(modifier = Modifier
                .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            )
            {
                if (songsLoader == true)
                {
                    Text("Загрузка следущих композиций")
                    CircularProgressIndicator(modifier = Modifier.size(100.dp))
                    return@Column
                }


                val songsInQueue = currentQueue
                val pagerState = rememberPagerState(
                    initialPage = posInQueue,
                    pageCount = { songsInQueue.size }
                )

                val nestedScrollConnection = remember {
                    object :
                        NestedScrollConnection {}
                }

                HorizontalPager(
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.2f,
                        snapAnimationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(
                            nestedScrollConnection
                        )
                        .pointerInput(
                            Unit
                        ) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                            }
                        }
                ) { page ->

                    val songInQueue = allAudioData[songsInQueue[page].hashKey]

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(screenHeight * 0.5f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            asyncedImage(
                                songInQueue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                blurRadius = 0f,
                                blendGrad = true,
                                turnOffPlaceholders = true

                            )
                        }

                    }
                }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            if (page < 0 || page >= songsInQueue.size) return@collect

                            if (page == mainViewModel.uiState.value.posInQueue) return@collect

                            mainViewModel.setPosInQueue(page)

                            try {
                                AppViewModels.player.playerManager.playAtIndex(mainViewModel, searchViewModel, page)
                            } catch (e: Throwable) {

                            }
                        }
                }

                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(posInQueue) {
                    val newIndex = posInQueue
                    if (newIndex != -1 && newIndex != pagerState.settledPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                newIndex,
                                animationSpec = tween(
                                    durationMillis = 500,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    }
                }

                Column(Modifier.offset(y = -16.dp).fillMaxSize())
                {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 25.dp))
                    {
                        Column()
                        {
                            Text(text = song?.title ?: "", fontSize = 18.sp, maxLines = 1, modifier = Modifier.clickable {
                                openAlbum(song?.album_original_link ?: "")
                                onToogleSheet()
                            }.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                repeatDelayMillis = 2000,
                                velocity = 40.dp
                            )
                            )


                            Spacer(
                                Modifier.height(8.dp))

                            Row()
                            {
                                song?.artists?.forEach { artist ->

                                    Text(
                                        artist.title + if (artist != song.artists.last()) ", " else "",
                                        maxLines = 1, color = Color(255, 255, 255, 90), modifier = Modifier.clickable {
                                            openArtist(artist.url)
                                            onToogleSheet()
                                        }
                                    )

                                }
                            }
                        }

                    }

                    Spacer(Modifier.height(8.dp))

                    Column(modifier = Modifier.fillMaxWidth())
                    {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                        {
                            Box(modifier = Modifier.fillMaxWidth())
                            {
                                Slider(
                                    value = songProgressUi.value.progress,
                                    onValueChange = { newProgress ->

                                        val duration = AppViewModels.player.playerManager.player!!.duration
                                        val newPosition = (duration * newProgress).toLong()

                                        setSongProgress(newProgress, newPosition)
                                        AppViewModels.player.playerManager.player!!.seekTo(newPosition)

                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    steps = 0,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(
                                            255,
                                            255,
                                            255,
                                            255
                                        ),
                                        inactiveTrackColor = Color(255, 255, 255, 30)
                                    )
                                )
                            }
                        }

                        Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp))
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

                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {

                        IconButton(
                            onClick =
                            {
                                AppViewModels.player.playerManager.prevSong(mainViewModel, searchViewModel)
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(color = Color(255, 255, 255, 30), shape = CircleShape)
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

                        Spacer(Modifier.width(16.dp))


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
                                .background(color = Color(255, 255, 255, 30), shape = CircleShape)
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

                        Spacer(Modifier.width(16.dp))

                        IconButton(
                            onClick =
                            {
                                AppViewModels.player.playerManager.nextSong(mainViewModel, searchViewModel)
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(color = Color(255, 255, 255, 30), shape = CircleShape)

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

                    Spacer(modifier = Modifier.weight(1f))

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


            }
        }

    }
}