package com.example.groviq.frontEnd.bottomBars

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.dataStructures.songProgressState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.formatTime
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.playerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun audioBottomSheet(mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, content: @Composable () -> Unit) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var showSheet by rememberSaveable { mutableStateOf(false) }

    mainSheetDraw(sheetState, showSheet, {showSheet = !showSheet}, mainViewModel,searchViewModel, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mainSheetDraw(sheetState: SheetState,  showSheet: Boolean, onToogleSheet: () -> Unit, mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, content: @Composable () -> Unit) {

    val mainUiState     by mainViewModel.uiState.collectAsState()
    val songProgressUi            = songProgressState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        content()

        if (mainUiState.playingHash.isNullOrEmpty())
            return@Box

        if (mainUiState.allAudioData[mainUiState.playingHash] == null)
            return@Box

        //mini box
        if (true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 15.dp, end = 15.dp)
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(35, 35, 35)
                    )
                    .clickable {
                        onToogleSheet()
                    }
            )
            {

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

                    IconButton(
                        onClick =
                        {
                            playerManager.prevSong(mainViewModel, searchViewModel = searchViewModel )
                        },
                    ) {
                        Icon(
                            imageVector =
                            Icons.Rounded.SkipPrevious
                            ,
                            contentDescription = "SkipPrev",
                            tint = Color(255, 255, 255)
                        )
                    }

                    if (mainUiState.currentStatus == playerStatus.IDLE || mainUiState.currentStatus == playerStatus.BUFFERING)
                    {
                        CircularProgressIndicator()
                    }
                    else
                    {
                        IconButton(
                            onClick =
                            {
                                if (mainUiState.currentStatus == playerStatus.PAUSE)
                                    playerManager.resume()
                                if (mainUiState.currentStatus == playerStatus.PLAYING)
                                    playerManager.pause()
                            },
                        ) {
                            Icon(
                                imageVector = if (mainUiState.currentStatus == playerStatus.PAUSE)
                                    Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                ,
                                contentDescription = "Pause/Play",
                                tint = Color(255, 255, 255)
                            )
                        }
                    }


                    IconButton(
                        onClick =
                        {
                            playerManager.nextSong(mainViewModel, searchViewModel)
                        },
                    ) {
                        Icon(
                            imageVector =
                            Icons.Rounded.SkipNext
                            ,
                            contentDescription = "SkipNext",
                            tint = Color(255, 255, 255)
                        )
                    }



                    Column()
                    {
                        val song = mainUiState.allAudioData[mainUiState.playingHash]

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

        //scaffold
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { onToogleSheet() },
                sheetState = sheetState,
                modifier = Modifier.fillMaxSize(),
                dragHandle = null,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                )
                {

                    Column(modifier = Modifier
                        .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    {
                        if (mainUiState.songsLoader == true)
                        {
                            Text("Загрузка следущих композиций")
                            CircularProgressIndicator(modifier = Modifier.size(100.dp))
                            return@Column
                        }


                        val song = mainUiState.allAudioData[mainUiState.playingHash]

                        if (song!!.art != null)
                        {
                            Image(song!!.art!!.asImageBitmap(), null)
                        }

                        Text(song.title, Modifier.clickable {
                            openAlbum(song.album_original_link)
                            onToogleSheet()
                        })


                        Spacer(Modifier.height(30.dp))

                        Row()
                        {
                            song.artists.forEach { artist ->

                                Text(
                                    artist.title + if (artist != song.artists.last()) ", " else "",
                                    maxLines = 1, color = Color(255, 255, 255), modifier = Modifier.clickable {
                                        openArtist(artist.url)
                                        onToogleSheet()
                                    }
                                )

                            }
                        }

                        Spacer(Modifier.height(30.dp))

                        Slider(
                            value = songProgressUi.value.progress,
                            onValueChange = { newProgress ->

                                val duration = playerManager.player!!.duration
                                val newPosition = (duration * newProgress).toLong()

                                setSongProgress(newProgress, newPosition)
                                playerManager.player!!.seekTo(newPosition)

                            },
                            modifier = Modifier.fillMaxWidth(),
                            steps = 0,
                            valueRange = 0f..1f,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatTime(songProgressUi.value.position))
                            Text(text = formatTime(song.duration))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            IconButton(
                                onClick =
                                {
                                    playerManager.prevSong(mainViewModel, searchViewModel)
                                },
                            ) {
                                Icon(
                                    imageVector =
                                    Icons.Rounded.SkipPrevious
                                    ,
                                    contentDescription = "SkipPrev",
                                    tint = Color(255, 255, 255)
                                )
                            }

                            IconButton(
                                onClick =
                                {
                                    if (mainUiState.currentStatus == playerStatus.PAUSE)
                                        playerManager.resume()
                                    if (mainUiState.currentStatus == playerStatus.PLAYING)
                                        playerManager.pause()
                                },
                            ) {
                                Icon(
                                    imageVector = if (mainUiState.currentStatus == playerStatus.PAUSE)
                                        Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                    ,
                                    contentDescription = "Pause/Play",
                                    tint = Color(255, 255, 255)
                                )
                            }

                            IconButton(
                                onClick =
                                {
                                    playerManager.nextSong(mainViewModel, searchViewModel)
                                },
                            ) {
                                Icon(
                                    imageVector =
                                    Icons.Rounded.SkipNext
                                    ,
                                    contentDescription = "SkipNext",
                                    tint = Color(255, 255, 255)
                                )
                            }


                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
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
                                    tint = Color(255, 255, 255, if (mainUiState.isShuffle) 255 else 100)
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
                                    if (mainUiState.repeatMode == repeatMods.REPEAT_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "SkipPrev",
                                    tint = Color(255, 255, 255,
                                        if (mainUiState.repeatMode == repeatMods.NO_REPEAT)       100
                                        else if (mainUiState.repeatMode == repeatMods.REPEAT_ALL) 255
                                        else 255)
                                )
                            }
                        }

                    }
                }


            }
        }
    }
}