package com.example.groviq.frontEnd.bottomBars

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.playEngine.addToCurrentQueue
import com.example.groviq.backEnd.playEngine.moveInQueue
import com.example.groviq.backEnd.playEngine.removeFromQueue
import com.example.groviq.backEnd.streamProcessor.DownloadManager
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.appScreens.openRadio
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.showSheet
import com.example.groviq.frontEnd.subscribeMe
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

var isTrackSettingsOpened: MutableState<Boolean> = mutableStateOf(false)
var currentTrackHashForSettings: MutableState<String?> = mutableStateOf(null)
var currentSettingsOpenedAudioSource: MutableState<String?> = mutableStateOf(null)


fun openTrackSettingsBottomBar(requestHash: String, audioSource : String = "IT_EMPTY_AUDIOSOURCE_") {
    currentTrackHashForSettings.value = requestHash
    isTrackSettingsOpened.value = true
    currentSettingsOpenedAudioSource.value = audioSource
}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun trackSettingsBottomBar(mainViewModel : PlayerViewModel)
{
    val isOpened by rememberUpdatedState(isTrackSettingsOpened.value)
    val requestHash = currentTrackHashForSettings.value ?: return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(isOpened) {
        if (isOpened) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }


    ModalBottomSheet(
        onDismissRequest = {
            isTrackSettingsOpened.value             = false
            currentTrackHashForSettings.value       = null
            currentSettingsOpenedAudioSource.value  = null
        },
        sheetState = sheetState,
        containerColor = Color(
            0xFF171717
        ),
    ) {

        Box {

            Column(modifier = Modifier.padding(10.dp)) {
                drawSettingsBottomBar(mainViewModel, requestHash, {
                    isTrackSettingsOpened.value             = false
                    currentTrackHashForSettings.value       = null
                    currentSettingsOpenedAudioSource.value  = null
                } )
            }

        }

    }

}


@Composable
fun buttonForSettingBar(title : String, imageVector: ImageVector, onClick : () -> Unit )
{
    Button(
        onClick = {
            onClick()
        },
        colors = ButtonColors(
            containerColor = Color(0, 0, 0, 0),
            contentColor = Color(255, 255, 255, 255),
            disabledContainerColor = Color(0, 0, 0, 0),
            disabledContentColor = Color(255, 255, 255, 255),
        ),
        modifier = Modifier.padding(
            bottom = 10.dp
        ).fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 10.dp
        )
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        )
        {
            Icon(
                imageVector,
                "Icons_Selector1"
            )
            Text(
                title,
                fontSize = 15.sp,
                modifier = Modifier.padding(
                    start = 15.dp
                ),
                color = Color(
                    255,
                    255,
                    255,
                    255
                )
            )
        }
    }
}

enum class settingPages{
    MAIN_PAGE_SCREEN,
    QUEUE_SHOW_LIST_SCREEN,
    SELECT_ARTIST_TO_MOVE,
    ADD_TO_PLAYLIST_SCREEN,
    DOWNLOAD_QUEUE_PAGE
}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun drawSettingsBottomBar(mainViewModel : PlayerViewModel, requestHash : String, onClose : () -> Unit)
{
    // current reactive variables for subscribe //
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }

    if (allAudioData[requestHash] == null) {
        onClose()
        return
    }

    val track = allAudioData[requestHash]

    var settingPage by remember { mutableStateOf(settingPages.MAIN_PAGE_SCREEN) }

    val liked by mainViewModel.isAudioSourceContainsSong(track!!.link, "Favourite")
        .collectAsState(initial = false)

    BackHandler {
        settingPage = settingPages.MAIN_PAGE_SCREEN
    }

    when (settingPage)
    {
        settingPages.MAIN_PAGE_SCREEN       -> { drawMainSettingsPage(mainViewModel, liked, track, onClose, { page -> settingPage = page} ) }
        settingPages.QUEUE_SHOW_LIST_SCREEN -> { drawQueuePage(mainViewModel, { page -> settingPage = page})}
        settingPages.ADD_TO_PLAYLIST_SCREEN -> { drawPlaylistsToAdd(mainViewModel, track, onClose, { page -> settingPage = page}) }
        settingPages.SELECT_ARTIST_TO_MOVE ->  { drawSelectArtistPage(mainViewModel, track, onClose, { page -> settingPage = page})}
        settingPages.DOWNLOAD_QUEUE_PAGE   ->  { drawDownloadQueuePage(mainViewModel) }
    }


}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@Composable
fun drawMainSettingsPage(mainViewModel : PlayerViewModel, liked: Boolean, track: songData?, onClose : () -> Unit, onScreenMove : (settingPages) -> Unit)
{

    Row(Modifier.padding(start = 8.dp))
    {
        asyncedImage(
            track,
            Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
        )

        Column(Modifier.padding(horizontal = 20.dp))
        {
            Text(track?.title ?: "", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                repeatDelayMillis = 2000,
                velocity = 40.dp
            ))
            Text(
                text = track?.artists?.joinToString { it.title } ?: "",
                fontSize = 17.sp,
                maxLines = 1,
                color = Color.Gray
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    if (!liked) {
        buttonForSettingBar("Добавить в любимое", Icons.Rounded.FavoriteBorder, {
            mainViewModel.addSongToAudioSource(track!!.link, "Favourite")
            mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[track!!.link]!!)
            mainViewModel.saveAudioSourcesToRoom()
            onClose()
        })
    }
    else {
        buttonForSettingBar("Убрать из любимых", Icons.Rounded.Favorite, {
            mainViewModel.removeSongFromAudioSource(track!!.link, "Favourite")
            mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[track!!.link]!!)
            mainViewModel.saveAudioSourcesToRoom()
            onClose()
        })
    }


    if (mainViewModel.getPlaylists().containsKey(currentSettingsOpenedAudioSource.value))
    {
        buttonForSettingBar("Удалить из плейлиста", Icons.Rounded.PlaylistAdd, {
            mainViewModel.removeSongFromAudioSource(track!!.link, currentSettingsOpenedAudioSource.value!! )
            mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[track!!.link]!!)
            mainViewModel.saveAudioSourcesToRoom()
            onClose()
        })
    }

    buttonForSettingBar("Добавить в плейлист", Icons.Rounded.PlaylistAdd, {
        onScreenMove(settingPages.ADD_TO_PLAYLIST_SCREEN)
    })


    if (track!!.file != null) {

        if (track!!.file!!.exists()) {
            buttonForSettingBar("Удалить с устройства", Icons.Rounded.FileDownloadOff, {
                DownloadManager.deleteDownloadedAudioFile(mainViewModel, track.link)
                onClose()
            })
        }
    }
    else {
        buttonForSettingBar("Скачать на устройство", Icons.Rounded.Download, {

            DownloadManager.enqueue(mainViewModel, track.link)
            DownloadManager.start()

            onClose()
        })
    }

    buttonForSettingBar("Перейти к альбому", Icons.Rounded.Album, {
        showSheet.value = false
        openAlbum(track!!.album_original_link)
        onClose()
    })

    buttonForSettingBar("Перейти к артисту", Icons.Rounded.People, {

        if (track!!.artists.size > 1)
        {
            onScreenMove(settingPages.SELECT_ARTIST_TO_MOVE)
        }
        else {
            showSheet.value = false
            openArtist(track!!.artists.first().url)
            onClose()
        }
    })

    buttonForSettingBar("Добавить в очередь", Icons.Rounded.Queue, {
        addToCurrentQueue(mainViewModel, track!!.link, "")
        onClose()
    })

    buttonForSettingBar("Просмотреть очередь", Icons.Rounded.QueueMusic, {
        onScreenMove(settingPages.QUEUE_SHOW_LIST_SCREEN)
    })

    buttonForSettingBar("Очередь загрузок", Icons.Rounded.CloudQueue, {
        onScreenMove(settingPages.DOWNLOAD_QUEUE_PAGE)
    })

    buttonForSettingBar("Перейти к радио по треку", Icons.Rounded.Radio, {
        showSheet.value = false
        openRadio(track.link)
        onClose()
    })
}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@Composable
fun drawSelectArtistPage(mainViewModel : PlayerViewModel, track: songData?, onClose : () -> Unit, onScreenMove : (settingPages) -> Unit)
{
    Column(modifier = Modifier.padding(20.dp))
    {
        track!!.artists.forEach { artist ->
            Row( verticalAlignment = Alignment.CenterVertically)
            {
                Icon(Icons.Rounded.Person, "", tint = Color(0, 0, 0), modifier = Modifier.background(
                    shape = CircleShape, color = Color(255, 255, 255) ).size(60.dp) )

                Spacer(Modifier.width(15.dp))

                Text(text = artist.title, color = Color(255, 255, 255), fontSize = 21.sp, modifier = Modifier.clickable {
                    openArtist(artist.url)
                    onClose()
                    showSheet.value = false
                })
            }
            Spacer(Modifier.height(15.dp))
        }
    }



}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@Composable
fun drawPlaylistsToAdd(mainViewModel : PlayerViewModel, track: songData?, onClose : () -> Unit, onScreenMove : (settingPages) -> Unit)
{
    val playlists    = mainViewModel.getPlaylists()

    playlists.forEach { playlist ->
        Row(modifier = Modifier.clickable {

            mainViewModel.addSongToAudioSource(track!!.link, playlist.key)

            mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[track!!.link]!!)
            mainViewModel.saveAudioSourcesToRoom()

            onClose()
        })
        {
            Icon(Icons.Rounded.PlaylistPlay, "", Modifier.size(55.dp))

            Text(text = playlist.key, color = Color(255, 255, 255), fontSize = 21.sp)
        }
    }

}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@Composable
fun drawQueuePage(
    mainViewModel: PlayerViewModel,
    onScreenMove: (settingPages) -> Unit
) {

    val currentQueue       by mainViewModel.uiState.subscribeMe  { it.currentQueue  }
    val posInQueue         by mainViewModel.uiState.subscribeMe  { it.posInQueue    }
    val allAudioData       by mainViewModel.uiState.subscribeMe  { it.allAudioData   }

    val lazyListState = rememberLazyListState()
    var isReordering by remember { mutableStateOf(false) }

    val filteredQueue = remember(currentQueue, posInQueue) {
        val start = (posInQueue + 1).coerceAtMost(currentQueue.size)
        val endExclusive = minOf(currentQueue.size, start + 40)
        if (start < endExclusive) currentQueue.subList(start, endExclusive).toList()
        else emptyList()
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val queue = currentQueue
        if (queue.isEmpty()) return@rememberReorderableLazyListState

        // согласуем start и endExclusive с filteredQueue
        val start = (posInQueue + 1).coerceAtMost(queue.size)
        val endExclusive = minOf(queue.size, start + 40)
        val localFilteredSize = endExclusive - start

        if (from.index !in 0 until localFilteredSize || to.index !in 0 until localFilteredSize) return@rememberReorderableLazyListState

        val fromOriginalIndex = start + from.index
        val toOriginalIndex = start + to.index

        if (fromOriginalIndex !in queue.indices || toOriginalIndex !in queue.indices) return@rememberReorderableLazyListState

        moveInQueue(mainViewModel, fromOriginalIndex, toOriginalIndex)
    }

    val haptic = LocalHapticFeedback.current

    Text("Далее в очереди...", color = Color(255, 255, 255), modifier = Modifier.padding(15.dp))

    if (filteredQueue.isEmpty())
        return

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
    ) {

        if (filteredQueue.isEmpty().not())
        {
            itemsIndexed(
                items = filteredQueue,
                key = { _, queueElement -> queueElement.id }
            ) { indexFiltered, queueElement ->
                val track = allAudioData[queueElement.hashKey]


                ReorderableItem(reorderableState, key = queueElement.id) { isDragging ->
                    val elevation   by animateDpAsState(    if (isDragging) 4.dp else 0.dp  )
                    val scale       by animateFloatAsState( if (isDragging) 1.05f else 1f   )

                    Row(
                        modifier = Modifier
                            .shadow(elevation)
                            .scale(scale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        //detele using original index
                        IconButton(
                            onClick = {
                                val originalIndex = posInQueue + 1 + indexFiltered
                                removeFromQueue(mainViewModel, originalIndex)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "delete from queue",
                                tint = Color(255, 255, 255, 100)
                            )
                        }

                        asyncedImage(
                            track,
                            Modifier.size(35.dp)
                        )


                        track?.let { t ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                        repeatDelayMillis = 2000,
                                        velocity = 40.dp
                                    )
                                )
                                Text(
                                    text = t.artists.joinToString { it.title },
                                    maxLines = 1,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    isReordering = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    isReordering = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            ),
                            onClick = {}
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Reorder",
                                tint = Color(255, 255, 255, 100)
                            )
                        }
                    }
                }
            }
        }
        else
        {
            item {
                Row()
                {
                    Icon(Icons.Rounded.CleaningServices, "")
                    Text("Ой..Тут пусто")
                }
            }
        }
    }
}

@androidx.annotation.OptIn(
    UnstableApi::class
)
@Composable
fun drawDownloadQueuePage(mainViewModel: PlayerViewModel) {

    val downloadState by DownloadManager.state.collectAsState()

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    Text(
        "Очередь скачивания...",
        color = Color.White,
        modifier = Modifier.padding(15.dp)
    )

    val queue = downloadState.queued
    val active = downloadState.active

    if (queue.isEmpty() && active.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Text("Очередь пуста", color = Color.Gray)
            }
        }
        return
    }

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
        val combinedQueue = active.toList() + queue

        itemsIndexed(combinedQueue, key = { _, hash -> hash }) { index, hash ->
            val track = mainViewModel.uiState.value.allAudioData[hash]
            val progress = track?.progressStatus?.downloadingProgress ?: 0f
            val isDownloading = hash in active

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка отмены
                IconButton(
                    onClick = {
                        DownloadManager.cancel(hash)
                    }
                ) {
                    Icon(
                        imageVector = if (isDownloading) Icons.Rounded.Close else Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                // Обложка
                asyncedImage(track, Modifier.size(35.dp))

                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(track?.title ?: "Unknown", color = Color.White)
                    Text(
                        track?.artists?.joinToString { it.title } ?: "",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )

                    LinearProgressIndicator(
                        progress = if (isDownloading) progress / 100f else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(top = 4.dp),
                        color = if (isDownloading) Color.Green else Color.Gray
                    )
                }
            }
        }
    }
}

