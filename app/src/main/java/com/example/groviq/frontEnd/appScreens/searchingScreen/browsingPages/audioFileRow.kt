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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.playEngine.addToCurrentQueue
import com.example.groviq.frontEnd.bottomBars.openTrackSettingsBottomBar
import com.example.groviq.globalContext
import com.example.groviq.vibrateLight


@Composable
fun SquareProgressBox(
    progress: Int,
    modifier: Modifier = Modifier,
    size: Dp = 45.dp,
    cornerRadius: Dp = 6.dp
) {
    if (progress == 0)
        return

    if (progress == 100)
        return

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (2.dp).toPx()
            val length = size.toPx()
            val radius = cornerRadius.toPx()
            val p = progress.coerceIn(0, 100) / 100f

            // Общий периметр с учётом углов (длина дуги = четверть окружности)
            val straightEdge = length - 2 * radius
            val arcLength = (Math.PI.toFloat() / 2f) * radius
            val perimeter = 4 * straightEdge + 4 * arcLength

            var remaining = perimeter * p
            val path = Path()

            fun moveToNext(x: Float, y: Float) {
                if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Угол 1: верхний левый (дуга)
            val arcSteps = 15
            fun drawArcSegment(cx: Float, cy: Float, startAngle: Float, sweepAngle: Float) {
                val step = sweepAngle / arcSteps
                for (i in 0..arcSteps) {
                    val angle = Math.toRadians((startAngle + step * i).toDouble())
                    val x = (cx + radius * Math.cos(angle)).toFloat()
                    val y = (cy + radius * Math.sin(angle)).toFloat()
                    moveToNext(x, y)
                }
            }

            // Отрисовываем прогресс по кускам: дуга + прямая и т.д.

            // Верхний левый угол
            if (remaining > 0f) {
                val seg = arcLength.coerceAtMost(remaining)
                drawArcSegment(radius, radius, 180f, 90f * (seg / arcLength))
                remaining -= seg
            }

            // Верхняя прямая
            if (remaining > 0f) {
                val seg = straightEdge.coerceAtMost(remaining)
                moveToNext(radius + seg, 0f)
                remaining -= seg
            }

            // Верхний правый угол
            if (remaining > 0f) {
                val seg = arcLength.coerceAtMost(remaining)
                drawArcSegment(length - radius, radius, 270f, 90f * (seg / arcLength))
                remaining -= seg
            }

            // Правая прямая
            if (remaining > 0f) {
                val seg = straightEdge.coerceAtMost(remaining)
                moveToNext(length, radius + seg)
                remaining -= seg
            }

            // Нижний правый угол
            if (remaining > 0f) {
                val seg = arcLength.coerceAtMost(remaining)
                drawArcSegment(length - radius, length - radius, 0f, 90f * (seg / arcLength))
                remaining -= seg
            }

            // Нижняя прямая
            if (remaining > 0f) {
                val seg = straightEdge.coerceAtMost(remaining)
                moveToNext(length - radius - seg, length)
                remaining -= seg
            }

            // Нижний левый угол
            if (remaining > 0f) {
                val seg = arcLength.coerceAtMost(remaining)
                drawArcSegment(radius, length - radius, 90f, 90f * (seg / arcLength))
                remaining -= seg
            }

            // Левая прямая
            if (remaining > 0f) {
                val seg = straightEdge.coerceAtMost(remaining)
                moveToNext(0f, length - radius - seg)
                remaining -= seg
            }

            // Фоновый контур (весь серый квадрат)
            drawRoundRect(
                color = Color.LightGray,
                size = Size(length, length),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = strokeWidth)
            )

            // Прогресс по периметру
            drawPath(
                path = path,
                color = Color(
                    0xFF28F609
                ),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

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

    val liked by mainViewModel.isAudioSourceContainsSong(song.link, "Favourite")
        .collectAsState(initial = false)

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

                                if ( liked )
                                    mainViewModel.removeSongFromAudioSource(song.link, "Favourite")
                                else
                                    mainViewModel.addSongToAudioSource(song.link, "Favourite")

                                vibrateLight(globalContext!!)

                                mainViewModel.saveSongToRoom(song)
                                mainViewModel.saveAudioSourcesToRoom()
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

        //the content of song
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

                    Box()
                    {
                        Image(
                            song.art!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(35.dp)
                        )

                        SquareProgressBox(song.progressStatus.downloadingProgress.toInt(),
                            size = 36.dp)

                    }


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


            Row()
            {
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
                IconButton(
                    onClick = {
                        openTrackSettingsBottomBar(song.link, audioSource)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "fav",
                        tint = Color(255, 255, 255, 255)
                    )
                }
            }

        }
    }
}