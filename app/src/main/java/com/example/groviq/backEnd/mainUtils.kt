package com.example.groviq

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

@OptIn(
    UnstableApi::class
)
fun getArtFromURI(uri: Uri): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(MyApplication.globalContext, uri)
        val bytes = retriever.embeddedPicture
        retriever.release()
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: Exception) {
        null
    }
}


fun getPythonModule(context: Context): PyObject {

    if (!Python.isStarted()) {
        Python.start(
            AndroidPlatform(context)
        )
    }

    val py = Python.getInstance()

    return py.getModule("pythonAPI")
}

fun hasInternetConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        return networkInfo.isConnected
    }
}

fun String.toSafeFileName(): String {
    return this.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}


fun getYoutubeObjectId(url: String): String {
    val normalized = url.substringBefore("&")

    return when {
        "watch?v=" in normalized -> {
            val id = Regex("v=([a-zA-Z0-9_-]{11})")
                .find(normalized)?.groupValues?.get(1)
            if (id != null) "SONG:$id" else "UNKNOWN"
        }

        "/browse/" in normalized -> {
            val id = Regex("browse/([A-Za-z0-9_-]+)")
                .find(normalized)?.groupValues?.get(1)
            if (id != null && id.startsWith("MPREb")) "ALBUM:$id" else "UNKNOWN"
        }

        "/channel/" in normalized -> {
            val id = Regex("channel/([A-Za-z0-9_-]+)")
                .find(normalized)?.groupValues?.get(1)
            if (id != null) "ARTIST:$id" else "UNKNOWN"
        }

        else -> "UNKNOWN"
    }
}



fun String.toHashFileName(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

suspend fun Context.loadBitmapFromCacheOrNetwork(
    imageKey: String,
    downsample: Int = 1
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val loader = ImageLoader(this@loadBitmapFromCacheOrNetwork)
        val request = ImageRequest.Builder(this@loadBitmapFromCacheOrNetwork)
            .data(imageKey)
            .allowHardware(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .size(500 / downsample)
            .build()

        val result = loader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable
        (drawable as? BitmapDrawable)?.bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun loadBitmapFromUrl(
    imageUrl: String,
    downsample: Int = 1
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        MyApplication.globalContext!!.loadBitmapFromCacheOrNetwork(imageUrl, downsample)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@Composable
fun LocalActivity(): ComponentActivity {
    val context = LocalContext.current
    return remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        error("LocalActivity not found")
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun vibrateLight(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrationEffect = VibrationEffect.createOneShot(
            100,
            150
        )
        vibrator.vibrate(vibrationEffect)
    }
}

fun Bitmap.isSmall(maxWidth: Int = 250, maxHeight: Int = 250): Boolean {
    return width <= maxWidth && height <= maxHeight
}

// 1) Функция: Bitmap -> сжатый byte[]
fun bitmapToCompressedBytes(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 90): ByteArray {
    val baos = ByteArrayOutputStream()
    bitmap.compress(format, quality, baos)
    return baos.toByteArray()
}

fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

suspend fun <T> retryWithBackoff(
    retries: Int = 3,
    initialDelayMs: Long = 300,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(retries - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    // final attempt (let exception bubble)
    return block()
}

// --- ждём доступности сети (polling) ---
suspend fun waitForInternet(maxWaitMs: Long = 60_000L, checkIntervalMs: Long = 1000L) {
    val start = System.currentTimeMillis()
    while (!hasInternetConnection(MyApplication.globalContext!!) && (System.currentTimeMillis() - start) < maxWaitMs) {
        delay(checkIntervalMs)
    }
    if (!hasInternetConnection(MyApplication.globalContext!!)) {
        throw IOException("No internet available after waiting $maxWaitMs ms")
    }
}

suspend fun getImageSizeFromUrl(url: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()
        connection.inputStream.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return@withContext options.outWidth to options.outHeight
            }
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

