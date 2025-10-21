package com.example.groviq.frontEnd

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.size.Precision
import android.graphics.RenderEffect as AndroidRenderEffect
import coil.transform.*
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.commit451.coiltransformations.BlurTransformation
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchingRequest
import com.example.groviq.getArtFromURI
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Color as AndroidColor

class grooviqUI {

    companion object elements
    {
        //bottom bars

        object openedElements
        {

        }

        object closedElements
        {

        }

        object screenPlaceholders
        {

        }

        object albumCoverPresenter
        {

        }

        object URICoverGetter {

            fun saveAlbumArtPermanentFromUri(uri: Uri, fileName: String): String? {
                val context = MyApplication.globalContext ?: return null

                return try {
                    val dir = File(context.filesDir, "album_art")
                    if (!dir.exists()) dir.mkdirs()

                    var safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    if (safeName.isBlank()) safeName = "unknown_art"

                    val outputFile = File(dir, "$safeName.jpg")

                    if (outputFile.exists()) {
                        return outputFile.absolutePath
                    }

                    val bitmap = getArtFromURI(uri) ?: return null

                    outputFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    bitmap.recycle()
                    outputFile.absolutePath

                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            fun deleteAlbumArt(album: String?, year: String?) {
                val context = MyApplication.globalContext ?: return
                if (album == null && year == null) return

                try {
                    val fileName = (album ?: "") + (year ?: "")
                    var safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    if (safeName.isBlank()) safeName = "unknown_art"

                    val dir = File(context.filesDir, "album_art")
                    val file = File(dir, "$safeName.jpg")

                    if (file.exists()) {
                        val deleted = file.delete()
                        if (!deleted) {

                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


    }

}

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
    customLoadSizeX: Int = 0,
    customLoadSizeY: Int = 0,
) {


    if (songData == null) return

    val context = LocalContext.current
    val imageKey = songData.art_local_link ?: songData.art_link

    val data = if (imageKey?.startsWith("/") ?: false) File(imageKey) else imageKey

    val request = remember(imageKey, blurRadius, customLoadSizeX, customLoadSizeY) {
        ImageRequest.Builder(context)
            .data(data)
            .crossfade(500)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(blurRadius == 0f)
            .apply {
                if (blurRadius > 0f) {
                    size(256, 256)
                    transformations(BlurTransformation(context, blurRadius / 2))
                } else {
                    size(512, 512)
                }
            }
            .build()
    }

    val painter = rememberAsyncImagePainter(model = request)

    val mod = if (blendGrad) {
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.7f to Color.Black,
                        1.0f to Color.Transparent,
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
    } else {
        if (blurRadius == 0f) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxSize()
        }
    }

    Box(
        modifier = modifier.background(
            if (blendGrad || turnOffBackground) Color.Transparent
            else Color.LightGray.copy(alpha = 0.2f)
        ),
        contentAlignment = Alignment.Center
    ) {

        if (blurRadius == 0f && !turnOffPlaceholders) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> onEmptyImageCallback?.invoke()
                    ?: Icon(
                        Icons.Rounded.Album,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                        ,tint = Color(255, 255, 255, 100)
                    )
                is AsyncImagePainter.State.Error -> onEmptyImageCallback?.invoke() ?:

                if (songData.isExternal)
                    {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = "Error",
                            modifier = Modifier.fillMaxSize(0.7f)
                            ,tint = Color(255, 255, 255, 100)
                        )
                    }
                        else
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = "Error",
                        modifier = Modifier.fillMaxSize(0.7f)
                        ,tint = Color(255, 255, 255, 100)
                    )
                else -> Unit
            }
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier = mod,
            contentScale = ContentScale.Crop
        )
    }
}


@SuppressLint("UnusedCrossfadeTargetStateParameter")
@Composable
fun asyncedImage(
    link: String?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null,
    blurRadius: Float = 0f,
    contentScale: ContentScale = ContentScale.Crop
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
            .fillMaxSize()
            .blur(blurRadius.dp)

        Image(
            painter = painter,
            contentDescription = null,
            modifier = mod,
            contentScale = contentScale
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
    iconOutlineButton("Повторить еще раз...", { onClick() }, Icons.Rounded.Restore )
}

@Composable
fun iconOutlineButton(text : String, onClick: () -> Unit, icon : ImageVector? = null)
{
    OutlinedButton( { onClick() } ,
        modifier = Modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(255, 255, 255, 200),
            containerColor = Color(0, 0, 0, 0),

        ),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary)
    )
    {
        Row(Modifier.padding(), verticalAlignment = Alignment.CenterVertically)
        {
            if (icon != null)
            {
                Icon  (icon, "")
                Spacer(Modifier.width(10.dp))
            }

            Text(text, fontWeight = FontWeight.Medium)
        }
    }
}


@Composable
fun grooviqUI.elements.screenPlaceholders.errorsPlaceHoldersScreen(
    publicErrors: MutableMap<String, publucErrors>,
    path : String,
    retryCallback: () -> Unit,
    addRetryToNothingFound : Boolean = false
)
{

    if (publicErrors[path] != publucErrors.CLEAN)
    {
        if (publicErrors[path] == publucErrors.NO_INTERNET)
        {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally)
                {
                    Text(text = "Нет подключения к интернету", color = Color(255, 255, 255, 255))

                    Spacer(Modifier.height(16.dp))

                    errorButton() {
                        retryCallback()
                    }
                }
            }
        }
        else if (publicErrors[path] == publucErrors.NO_RESULTS)
        {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                Column(verticalArrangement = Arrangement.Center)
                {
                    Text(text = "Ничего не найдено", color = Color(255, 255, 255, 255))

                    if (addRetryToNothingFound)
                    {
                        errorButton() {
                            retryCallback()
                        }
                    }

                }
            }
        }
    }
}





@Composable
fun grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
    audioSourceId: String,
    audioData: Map<String, audioSource>,
    allAudioData: Map<String, songData>,
    modifier: Modifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))
        .fillMaxSize(),
    blur: Float = 0f,
    drawOnlyFirst: Boolean = false,
    thumbnailSize: Int = 256 // размер для загрузки картинок
) {
    val audioSource = audioData[audioSourceId] ?: return

    // Предварительно вычисляем уникальные песни
    val firstFourSongs = remember(audioSource, allAudioData) {
        audioSource.songIds.mapNotNull { allAudioData[it] }
            .distinctBy { it.album_original_link }
            .take(4)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        if (drawOnlyFirst && firstFourSongs.isNotEmpty()) {
            key(firstFourSongs[0]) {
                asyncedImage(
                    songData = firstFourSongs[0],
                    modifier = Modifier.fillMaxSize(),
                    blurRadius = blur,
                    customLoadSizeX = thumbnailSize,
                    customLoadSizeY = thumbnailSize
                )
            }
        } else {
            when (firstFourSongs.size) {
                4 -> {
                    Column(Modifier.fillMaxSize()) {
                        for (y in 0 until 2) {
                            Row(Modifier.weight(1f)) {
                                for (x in 0 until 2) {
                                    val index = y * 2 + x
                                    key(firstFourSongs[index]) {
                                        asyncedImage(
                                            songData = firstFourSongs[index],
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            blurRadius = blur,
                                            customLoadSizeX = thumbnailSize,
                                            customLoadSizeY = thumbnailSize
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Column(Modifier.fillMaxSize()) {
                        key(firstFourSongs[2]) {
                            asyncedImage(
                                songData = firstFourSongs[2],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                blurRadius = blur,
                                customLoadSizeX = thumbnailSize,
                                customLoadSizeY = thumbnailSize
                            )
                        }
                        Row(Modifier.weight(1f)) {
                            for (i in 0..1) {
                                key(firstFourSongs[i]) {
                                    asyncedImage(
                                        songData = firstFourSongs[i],
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        blurRadius = blur,
                                        customLoadSizeX = thumbnailSize,
                                        customLoadSizeY = thumbnailSize
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Row(Modifier.fillMaxSize()) {
                        for (i in 0..1) {
                            key(firstFourSongs[i]) {
                                asyncedImage(
                                    songData = firstFourSongs[i],
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    blurRadius = blur,
                                    customLoadSizeX = thumbnailSize,
                                    customLoadSizeY = thumbnailSize
                                )
                            }
                        }
                    }
                }
                1 -> {
                    key(firstFourSongs[0]) {
                        asyncedImage(
                            songData = firstFourSongs[0],
                            modifier = Modifier.fillMaxSize(),
                            blurRadius = blur,
                            customLoadSizeX = thumbnailSize,
                            customLoadSizeY = thumbnailSize
                        )
                    }
                }
                0 -> {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = "Empty Playlist",
                        modifier = Modifier.fillMaxSize(),
                        tint = Color(255, 255, 255)
                    )
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

    val imageKey = song?.art_local_link ?: song?.art_link

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


@Composable
fun InfiniteRoundedCircularProgress(
    modifier: Modifier = Modifier.size(100.dp),
    strokeWidth: Dp = 3.dp,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary)
) {
    val infiniteTransition = rememberInfiniteTransition()

    val sweep by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val startAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            brush = if (colors.size > 1) Brush.sweepGradient(colors) else SolidColor(colors.first()),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            style = stroke
        )
    }
}
