package com.example.groviq.frontEnd

import android.graphics.Bitmap
import android.graphics.RenderEffect.createBlurEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.content.MediaType.Companion.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.groviq.backEnd.dataStructures.songData
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import android.graphics.RenderEffect as AndroidRenderEffect
import coil.transform.*
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun asyncedImage(
    songData: songData?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null,
    blurRadius: Float = 0f,
) {
    if (songData == null) return

    val contentModifier = modifier
        .background(Color.LightGray.copy(alpha = 0.2f))

    when {

        songData.art != null -> {
            val imageModifier = if (blurRadius > 0f) {
                contentModifier.graphicsLayer {
                    renderEffect = AndroidRenderEffect.createBlurEffect(
                        blurRadius,
                        blurRadius,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            } else {
                contentModifier
            }

            Image(
                bitmap = songData.art!!.asImageBitmap(),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
        }

        songData.art_link.isNullOrEmpty().not() -> {

            if (blurRadius > 0)
            {
                val artState = remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(songData.art_link ?: "") {
                    songData.art_link?.let { url ->
                        artState.value = loadBitmapFromUrl(url)
                    }
                }

                val imageModifier = if (blurRadius > 0f) {
                    contentModifier.graphicsLayer {
                        renderEffect = AndroidRenderEffect.createBlurEffect(
                            blurRadius,
                            blurRadius,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                } else {
                    contentModifier
                }

                artState.value?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = contentModifier,
                    contentAlignment = Alignment.Center
                ) {
                    onEmptyImageCallback?.invoke() ?: Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }

            } else
            {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(songData.art_link)
                        .crossfade(true)
                        .build()
                )

                Box(
                    modifier = contentModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            onEmptyImageCallback?.invoke() ?:Icon(
                                Icons.Rounded.Image,
                                contentDescription = "Loading",
                                modifier = Modifier.fillMaxSize(0.7f)
                            )
                        }
                        is AsyncImagePainter.State.Error -> {
                            onEmptyImageCallback?.invoke() ?:Icon(
                                Icons.Rounded.ImageNotSupported,
                                contentDescription = "Error",
                                modifier = Modifier.fillMaxSize(0.7f)
                            )
                        }
                        else -> Unit
                    }
                }
            }

        }

        // 🔹 Нет изображения
        else -> {
            Box(contentModifier, contentAlignment = Alignment.Center) {
                onEmptyImageCallback?.invoke() ?: Icon(
                    Icons.Rounded.ImageNotSupported,
                    contentDescription = "Image not supported",
                    modifier = Modifier.fillMaxSize(0.7f)
                )
            }
        }
    }
}


@Composable
fun asyncedImage(
    link: String?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null
) {
    if (!link.isNullOrEmpty()) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(link)
                .crossfade(true)
                .build()
        )

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }

                is AsyncImagePainter.State.Error -> {
                    Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = "Error",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }

                else -> Unit
            }
        }
    } else {
        Box(
            modifier,
            contentAlignment = Alignment.Center
        ) {
            onEmptyImageCallback?.invoke() ?: Icon(
                Icons.Rounded.ImageNotSupported,
                contentDescription = "Image not supported",
                modifier = Modifier.fillMaxSize(0.7f)
            )
        }
    }
}

@Composable
fun errorButton( onClick: () -> Unit,)
{
    Button( { onClick() } )
    {
        Text("Повторить еще раз..")
    }
}




@Composable
fun UndoPopup(
    message: String,
    durationMs: Long = 5000L,
    onUndo: () -> Unit,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    var remainingTime by remember { mutableStateOf(durationMs) }

    LaunchedEffect(Unit) {
        val interval = 100L
        while (remainingTime > 0) {
            delay(interval)
            remainingTime -= interval
        }
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .shadow(8.dp, RoundedCornerShape(12.dp)),
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$message (${(remainingTime / 1000L) + 1})")
                Button(
                    onClick = {
                        onUndo()
                        onDismiss()
                    }
                ) {
                    Text("Отменить")
                }
            }
        }
    }
}