package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import com.example.groviq.backEnd.dataStructures.songData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.playEngine.addToCurrentQueue
import com.example.groviq.globalContext
import com.example.groviq.vibrateLight

@Composable
fun SwipeToQueueItem(
    audioSource : String,
    song: songData,
    mainViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val addToQueueSwipeThreshold = 200f
    val addToLikesSwipeThreshold = -200f
    val maxOffset      = 300f

    val offsetX = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-maxOffset, maxOffset)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > addToQueueSwipeThreshold) {

                                //Swipe accepted
                                addToCurrentQueue(mainViewModel, song.link, audioSource)
                                vibrateLight(globalContext!!)
                            }
                            if (offsetX.value < addToLikesSwipeThreshold) {

                                //Swipe accepted
                                mainViewModel.addSongToAudioSource(song.link, "Favourite")
                                vibrateLight(globalContext!!)
                            }
                            offsetX.animateTo(0f)
                        }
                    }
                )
            }
    ) {

        if (offsetX.value > 10f || offsetX.value < -10f) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Add to queue",  Modifier.size(30.dp))
                Icon(Icons.Rounded.Favorite, contentDescription   = "Add to Likes",  Modifier.size(30.dp))
            }
        }

        // Контент трека со смещением
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.art != null) {
                    Image(
                        song.art!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(35.dp)
                    )
                }

                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(song.title, maxLines = 1, fontSize = 12.sp)
                    Text(
                        song.artists.joinToString { it.title },
                        maxLines = 1,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            val liked by mainViewModel.isAudioSourceContainsSong(song.link, "Favourite")
                .collectAsState(initial = false)

            IconButton(
                onClick = {
                    if (liked)
                        mainViewModel.removeSongFromAudioSource(song.link, "Favourite")
                    else
                        mainViewModel.addSongToAudioSource(song.link, "Favourite")
                }
            ) {
                Icon(
                    imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "fav",
                    tint = Color(255, 255, 255, if (liked) 255 else 100)
                )
            }
        }
    }
}