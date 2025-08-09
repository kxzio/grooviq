package com.example.groviq.frontEnd.bottomBars

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.playEngine.addToCurrentQueue
import com.example.groviq.backEnd.playEngine.moveInQueue
import com.example.groviq.backEnd.playEngine.removeFromQueue
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.screenConnectorNavigation
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

var isTrackSettingsOpened: MutableState<Boolean> = mutableStateOf(false)
var currentTrackHashForSettings: MutableState<String?> = mutableStateOf(null)

fun openTrackSettingsBottomBar(requestHash: String) {
    currentTrackHashForSettings.value = requestHash
    isTrackSettingsOpened.value = true
}

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
            isTrackSettingsOpened.value = false
            currentTrackHashForSettings.value = null
        },
        sheetState = sheetState,
        containerColor = Color(
            0xFF171717
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            drawSettingsBottomBar(mainViewModel, requestHash, {
                isTrackSettingsOpened.value = false
                currentTrackHashForSettings.value = null
            } )
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
    ADD_TO_PLAYLIST_SCREEN
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun drawSettingsBottomBar(mainViewModel : PlayerViewModel, requestHash : String, onClose : () -> Unit)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    if (mainUiState.allAudioData[requestHash] == null) {
        onClose()
        return
    }

    val track = mainUiState.allAudioData[requestHash]

    var settingPage by remember { mutableStateOf(settingPages.MAIN_PAGE_SCREEN) }

    val liked by mainViewModel.isAudioSourceContainsSong(track!!.link, "Favourite")
        .collectAsState(initial = false)

    when (settingPage)
    {
        settingPages.MAIN_PAGE_SCREEN       -> { drawMainSettingsPage(mainViewModel, liked, track, onClose, { page -> settingPage = page} ) }
        settingPages.QUEUE_SHOW_LIST_SCREEN -> { drawQueuePage(mainViewModel, { page -> settingPage = page})}
        settingPages.ADD_TO_PLAYLIST_SCREEN -> {}
    }


}

@Composable
fun drawMainSettingsPage(mainViewModel : PlayerViewModel, liked: Boolean, track: songData?, onClose : () -> Unit, onScreenMove : (settingPages) -> Unit)
{

    Row()
    {
        Image(track!!.art!!.asImageBitmap(), null, Modifier.size(35.dp))

        Column()
        {
            Text(track.title, color = Color.White)
            Text(
                track.artists.joinToString { it.title },
                maxLines = 1,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }

    if (!liked) {
        buttonForSettingBar("Добавить в любимое", Icons.Rounded.FavoriteBorder, {
            mainViewModel.addSongToAudioSource(track!!.link, "Favourite")
            onClose()
        })
    }
    else {
        buttonForSettingBar("Убрать из любимых", Icons.Rounded.Favorite, {
            mainViewModel.removeSongFromAudioSource(track!!.link, "Favourite")
            onClose()
        })
    }

    buttonForSettingBar("Перейти к альбому", Icons.Rounded.Album, {
        openAlbum(track!!.album_original_link)
        onClose()
    })

    buttonForSettingBar("Перейти к артисту", Icons.Rounded.People, {
        openArtist(track!!.artists.first().url)
        onClose()
    })

    buttonForSettingBar("Добавить в очередь", Icons.Rounded.Queue, {
        addToCurrentQueue(mainViewModel, track!!.link, "")
        onClose()
    })

    buttonForSettingBar("Просмотреть очередь", Icons.Rounded.QueueMusic, {
        onScreenMove(settingPages.QUEUE_SHOW_LIST_SCREEN)
    })
}

@Composable
fun drawQueuePage(mainViewModel : PlayerViewModel, onScreenMove : (settingPages) -> Unit)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    var queue = mainUiState.currentQueue

    // update local queue when the ViewModel's queue changes
    LaunchedEffect(mainUiState.currentQueue) {
        queue = mainUiState.currentQueue
    }

    val lazyListState = rememberLazyListState()
    var isReordering by remember { mutableStateOf(false) }

    LaunchedEffect(mainUiState.currentQueue) {
        if (!isReordering) {
            queue = mainUiState.currentQueue
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Reorder the local queue
        queue = queue.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        // Update the ViewModel with the new queue order
        moveInQueue(mainViewModel, from.index, to.index)
    }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = queue,
            key = { _, queueElement -> queueElement.hashKey } // Assuming hashKey is unique
        ) { indexInQueue, queueElement ->
            val track = mainUiState.allAudioData[queueElement.hashKey]

            ReorderableItem(reorderableState, key = queueElement.hashKey) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                val scale by animateFloatAsState(if (isDragging) 1.05f else 1f)

                Row(
                    modifier = Modifier
                        .shadow(elevation)
                        .scale(scale)
                ) {
                    IconButton(
                        onClick = {
                            removeFromQueue(mainViewModel, indexInQueue)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "delete from queue",
                            tint = Color(255, 255, 255, 255)
                        )
                    }

                    Image(
                        bitmap = track!!.art!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(35.dp)
                    )

                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(track.title, color = Color.White)
                        Text(
                            text = track.artists.joinToString { it.title },
                            maxLines = 1,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    // Drag handle for reordering
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
                        onClick = {} // No click action needed, just for drag
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = "Reorder",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }


}