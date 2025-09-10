package com.example.groviq.frontEnd

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import coil.imageLoader
import coil.request.CachePolicy
import android.graphics.RenderEffect as AndroidRenderEffect
import coil.transform.*
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

@Composable
fun <T, R> StateFlow<T>.subscribeMe(
    selector: (T) -> R
): State<R> {
    val initial = remember { selector(value) }
    val mapped = remember(this) { map(selector).distinctUntilChanged() }
    return mapped.collectAsState(initial = initial)
}


@SuppressLint("UnusedCrossfadeTargetStateParameter")
@Composable
fun asyncedImage(
    songData: songData?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null,
    blurRadius: Float = 0f,
    turnOffPlaceholders: Boolean = false,
    blendGrad: Boolean = false,
    turnOffBackground: Boolean = false,
    customLoadSizeX : Int = 0,
    customLoadSizeY : Int = 0,
)  {
    if (songData == null) return

    val context = LocalContext.current

    val imageKey = songData.art ?: songData.art_link

    val req = if (customLoadSizeY == 0) ImageRequest.Builder(context)
        .data(imageKey)
        .crossfade(500)
        .setParameter("coil#disk_cache_key", imageKey)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()
    else
        ImageRequest.Builder(context)
            .data(imageKey)
            .size(customLoadSizeX, customLoadSizeY)
            .crossfade(500)
            .setParameter("coil#disk_cache_key", imageKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()


    val painter = rememberAsyncImagePainter(model = req)

    Box(
        modifier = modifier
            .background(if (blendGrad || turnOffBackground) Color.Transparent else Color.LightGray.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        val mod = if (blendGrad) {
            modifier
                .fillMaxSize()
                .blur(blurRadius.dp)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.7f to Color.Black,
                            1.0f to Color.Transparent,
                            startY = 0f,
                            endY = size.height * 1.0f
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        } else {

            if (blurRadius == 0f)

                modifier
                    .fillMaxSize()

                else

            modifier
                .fillMaxSize()
                .blur(blurRadius.dp)
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier = mod,
            contentScale = ContentScale.Crop
        )


        if (blurRadius == 0f && !turnOffPlaceholders) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> onEmptyImageCallback?.invoke()
                    ?: Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                is AsyncImagePainter.State.Error -> onEmptyImageCallback?.invoke()
                    ?: Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = "Error",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                else -> Unit
            }
        }
    }
}

@SuppressLint("UnusedCrossfadeTargetStateParameter")
@Composable
fun asyncedImage(
    link: String?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null,
    blurRadius: Float = 0f
) {
    if (link == null) return

    val context = LocalContext.current


    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(link)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .setParameter("coil#disk_cache_key", link)
            .build()
    )

    Box(
        modifier = modifier.background(Color.LightGray.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        val mod = Modifier
            .matchParentSize()
            .blur(blurRadius.dp)

        Image(
            painter = painter,
            contentDescription = null,
            modifier = mod,
            contentScale = ContentScale.Crop
        )

        if (blurRadius == 0f) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> onEmptyImageCallback?.invoke()
                    ?: Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                is AsyncImagePainter.State.Error -> onEmptyImageCallback?.invoke()
                    ?: Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = "Error",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                else -> Unit
            }
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

@SuppressLint(
    "UnusedCrossfadeTargetStateParameter"
)
@Composable
fun background(song: songData, alpha : Float = 0.88f, drawBlack : Boolean = true) {

    val imageKey = song?.art ?: song?.art_link

    Box(Modifier.fillMaxSize())
    {

        Crossfade(
            targetState = imageKey,
            animationSpec = tween(600)
        ) { currentImage ->

            Box(Modifier.fillMaxSize())
            {
                asyncedImage(
                    song,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha)
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    onEmptyImageCallback = {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    },
                    blurRadius = 40f,
                    turnOffBackground = true
                )


            }
        }

        if (drawBlack)
        {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color(0, 0, 0, 180),
                                startY = 0f,
                                endY = size.height * 1.0f
                            )
                        )
                    }
                    .blur(15.dp)
            )
        }

    }


}
