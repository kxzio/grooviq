package com.example.groviq.backEnd.streamProcessor

import android.os.Looper
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.hasInternetConnection
import com.example.groviq.isSmall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okio.BufferedSink
import okio.buffer
import okio.sink
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import com.example.groviq.getYoutubeObjectId
import com.example.groviq.loadBitmapFromUrl
import com.example.groviq.retryWithBackoff
import com.example.groviq.toHashFileName
import com.example.groviq.toSafeFileName
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

private val downloadHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // no overall call timeout
        .build()
}

fun md5(input: String): String {
    val bytes = MessageDigest
        .getInstance("MD5")
        .digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

var currentDownloading: Job? = null

private val downloadMutex = Mutex()

suspend fun Call.awaitResponseCancellable(): Response =
    suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resumeWith(Result.success(response))
            }
        })

        cont.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {}
        }
    }


@OptIn(UnstableApi::class)
suspend fun downloadAudioFile(mainViewModel: PlayerViewModel, hashToDownload: String) {

    if (!hasInternetConnection(MyApplication.globalContext!!)) return

    val song = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: return
    if (song.progressStatus.downloadingHandled == true) return

    withContext(Dispatchers.Main) {
        mainViewModel.updateStatusForSong(
            song.link,
            song.progressStatus.copy(downloadingHandled = true)
        )
    }

    withContext(Dispatchers.IO) {
        downloadMutex.withLock {

            val safeAlbum = getYoutubeObjectId(song.album_original_link)

            val urlHash = getYoutubeObjectId(song.link)

            val uniqueName = if (song.stream.isVideo ) "${safeAlbum}__${urlHash}.mp4" else "${safeAlbum}__${urlHash}.mp3"
            val partName = "$uniqueName.part"

            val finalFile = File(MyApplication.globalContext!!.getExternalFilesDir(null), uniqueName)
            val partFile = File(MyApplication.globalContext!!.getExternalFilesDir(null), partName)

            try {
                // --- получаем streamUrl с ретраями ---
                val streamUrl = retryWithBackoff(
                    retries = 3,
                    initialDelayMs = 500L
                ) {

                    if (song.progressStatus.streamHandled)
                    {
                        mainViewModel.awaitStreamUrlFor(hashToDownload)
                            ?: throw IOException("Stream URL is null")
                    }
                    else
                    {
                        if (song!!.shouldGetStream()) {

                            if (song.progressStatus.streamHandled.not()) {
                                //stream not handled yet by any thread, we should get it by yourself

                                //clear old stream if we had one
                                mainViewModel.updateStreamForSong(
                                    hashToDownload,
                                    ""
                                )
                                //request to get the new one
                                fetchAudioStream(mainViewModel, hashToDownload)

                                mainViewModel.awaitStreamUrlFor(hashToDownload)
                                    ?: throw IOException("Stream URL is null")
                            }
                            else
                                mainViewModel.awaitStreamUrlFor(hashToDownload)
                                    ?: throw IOException("Stream URL is null")
                        }
                        else
                            mainViewModel.awaitStreamUrlFor(hashToDownload)
                                ?: throw IOException("Stream URL is null")
                    }

                }

                if (!isActive) return@withLock

                // --- HEAD запрос ---
                val headRequest = Request.Builder().url(streamUrl).head().build()
                val (totalSize, supportsRange) = downloadHttpClient.newCall(headRequest).execute()
                    .use { headResponse ->
                        if (!headResponse.isSuccessful) {
                            throw IOException("HEAD failed: code ${headResponse.code}")
                        }
                        val contentLengthHeader = headResponse.header("Content-Length")
                        val acceptRanges = headResponse.header("Accept-Ranges")
                        val size = contentLengthHeader?.toLongOrNull() ?: -1L
                        val range = acceptRanges?.equals("bytes", true) == true && size > 0
                        size to range
                    }


                // --- уже скачан ---
                if (finalFile.exists() && totalSize > 0 && finalFile.length() == totalSize) {
                    runOnMain {
                        mainViewModel.updateFileForSong(song.link, finalFile)
                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)
                        mainViewModel.updateStatusForSong(
                            song.link,
                            song.progressStatus.copy(downloadingHandled = false)
                        )
                    }
                    return@withLock
                }

                val existingBytes = partFile.length().coerceAtLeast(0L)

                if (existingBytes > 0 && supportsRange && existingBytes == totalSize) {
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    runOnMain {
                        mainViewModel.updateFileForSong(song.link, finalFile)
                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)
                        mainViewModel.updateStatusForSong(
                            song.link,
                            song.progressStatus.copy(downloadingHandled = false)
                        )
                    }
                    return@withLock
                }

                // --- стратегия ---
                if (supportsRange && totalSize > 0L) {
                    // resume download
                    val downloadedAtomic = AtomicLong(existingBytes)
                    if (!partFile.exists()) {
                        partFile.parentFile?.mkdirs()
                        partFile.createNewFile()
                    }

                    val startPosition = existingBytes
                    retryWithBackoff(retries = 3, initialDelayMs = 400L) {
                        val rangeHeader = "bytes=$startPosition-${totalSize - 1}"
                        val response = downloadHttpClient.newCall(
                            Request.Builder()
                                .url(streamUrl)
                                .addHeader("Range", rangeHeader)
                                .build()
                        ).awaitResponseCancellable()

                        if (!response.isSuccessful && response.code != 416) {
                            val code = response.code
                            response.close()
                            throw IOException("Range failed: code $code")
                        }

                        val body = response.body ?: run {
                            response.close()
                            throw IOException("Empty body for range")
                        }

                        RandomAccessFile(partFile, "rw").use { raf ->
                            raf.seek(startPosition)
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var lastPercent = -1
                                var lastReportTime = 0L
                                val reportIntervalMs = 300L

                                while (isActive) {
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break
                                    raf.write(buffer, 0, bytesRead)
                                    val newTotal = downloadedAtomic.addAndGet(bytesRead.toLong())

                                    if (totalSize > 0) {
                                        val percent = ((newTotal * 100) / totalSize).toInt()
                                        val now = System.currentTimeMillis()
                                        if (percent != lastPercent && now - lastReportTime >= reportIntervalMs) {
                                            lastPercent = percent
                                            lastReportTime = now
                                            runOnMain {
                                                mainViewModel.updateDownloadingProgressForSong(
                                                    song.link,
                                                    percent.toFloat()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        response.close()
                    }

                    if (partFile.exists() && (totalSize < 0 || partFile.length() >= totalSize)) {
                        if (!partFile.renameTo(finalFile)) {
                            partFile.copyTo(finalFile, overwrite = true)
                            partFile.delete()
                        }
                    }

                    song.art_link?.let { link ->
                        try {
                            val bmp = loadBitmapFromUrl(link)

                            //path
                            val safeName = song.art_link?.toHashFileName() ?: "default_name"
                            val coverFile = File(
                                MyApplication.globalContext!!.getExternalFilesDir(null),
                                "${safeName}.jpg"
                            )

                            //save
                            if (bmp != null) {
                                FileOutputStream(coverFile).use { out ->
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            }

                            mainViewModel.uiState.value.allAudioData[song.link]!!.art_local_link = coverFile.absolutePath

                        } catch (e: Exception) {

                        }
                    }

                } else {
                    // full download
                    partFile.parentFile?.mkdirs()
                    if (partFile.exists()) partFile.delete()
                    partFile.createNewFile()

                    retryWithBackoff(retries = 3, initialDelayMs = 400L) {
                        val response = downloadHttpClient.newCall(
                            Request.Builder().url(streamUrl).build()
                        ).awaitResponseCancellable()

                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            throw IOException("GET failed: code $code")
                        }

                        val body = response.body ?: run {
                            response.close()
                            throw IOException("Empty body")
                        }

                        val bufferedSink = partFile.sink().buffer()
                        val source = body.source()
                        try {
                            source.use { src ->
                                bufferedSink.use { bs ->
                                    var totalRead = 0L
                                    val bufferSize = 64L * 1024L
                                    var lastPercent = -1
                                    var lastReportTime = 0L
                                    val reportIntervalMs = 300L

                                    while (isActive) {
                                        val read = src.read(bs.buffer(), bufferSize)
                                        if (read == -1L) break

                                        totalRead += read
                                        bs.emit()

                                        if (totalSize > 0) {
                                            val percent = ((totalRead * 100) / totalSize).toInt()
                                            val now = System.currentTimeMillis()
                                            if (percent != lastPercent && now - lastReportTime >= reportIntervalMs) {
                                                lastPercent = percent
                                                lastReportTime = now
                                                runOnMain {
                                                    mainViewModel.updateDownloadingProgressForSong(
                                                        song.link,
                                                        percent.toFloat()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            response.close()
                        }
                    }

                    if (partFile.exists()) {
                        if (!partFile.renameTo(finalFile)) {
                            partFile.copyTo(finalFile, overwrite = true)
                            partFile.delete()
                        }
                    }

                    song.art_link?.let { link ->
                        try {
                            val bmp = loadBitmapFromUrl(link)

                            //path
                            val safeName = song.art_link?.toHashFileName() ?: "default_name"
                            val coverFile = File(
                                MyApplication.globalContext!!.getExternalFilesDir(null),
                                "${safeName}.jpg"
                            )

                            //save
                            if (bmp != null) {
                                FileOutputStream(coverFile).use { out ->
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            }

                            mainViewModel.uiState.value.allAudioData[song.link]!!.art_local_link = coverFile.absolutePath

                        } catch (e: Exception) {

                        }
                    }
                    
                }

                // --- финальные обновления ---
                runOnMain {
                    val downloadedFile = if (finalFile.exists()) finalFile else partFile
                    mainViewModel.updateFileForSong(song.link, downloadedFile)
                    mainViewModel.updateDownloadingProgressForSong(song.link, 100f)
                    mainViewModel.updateStatusForSong(
                        song.link,
                        song.progressStatus.copy(downloadingHandled = false)
                    )
                    mainViewModel.addSongToAudioSource(song.link, "Downloaded")

                    val toSave = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: song
                    mainViewModel.saveSongToRoom(toSave)
                    mainViewModel.saveAudioSourcesToRoom()
                }

            } catch (e: CancellationException) {
                // cancelled
                partFile.delete()
            } catch (e: Exception) {
                partFile.delete()
                runOnMain {
                    Toast.makeText(
                        MyApplication.globalContext,
                        "Ошибка загрузки: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    val latest = mainViewModel.uiState.value.allAudioData[hashToDownload]
                    if (latest != null && latest.progressStatus.downloadingHandled == true) {
                        mainViewModel.updateStatusForSong(
                            latest.link,
                            latest.progressStatus.copy(downloadingHandled = false)
                        )
                    }
                }
            }
        }
    }
}


fun runOnMain(block: () -> Unit) {
    if (Looper.getMainLooper().thread == Thread.currentThread()) {
        block()
    } else {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }
}



private fun deleteFromMediaStoreIfExists(context: Context, file: File) {
    try {
        val resolver: ContentResolver = context.contentResolver
        val filePath = file.absolutePath

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(filePath), null)
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val uri: Uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                try {
                    resolver.delete(uri, null, null)
                } catch (_: SecurityException) {

                }
            }
        }
    } catch (_: Throwable) {

    }
}

private fun applicationContextPlaceholder(): Context {
    throw IllegalStateException("Replace applicationContextPlaceholder() with your app context provider (globalContext)")
}


object DownloadManager {

    val downloadJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = Channel<Pair<PlayerViewModel, String>>(Channel.UNLIMITED)

    private var currentJob: Job? = null

    private val activeDownloads = mutableListOf<String>()
    private val queuedDownloads = mutableListOf<String>()

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state

    data class DownloadState(
        val active: List<String> = emptyList(),
        val queued: List<String> = emptyList()
    )

    @OptIn(UnstableApi::class)
    fun enqueue(mainViewModel: PlayerViewModel, hash: String) {
        if (hash in activeDownloads || hash in queuedDownloads) return

        queuedDownloads.add(hash)
        updateState()

        queue.trySend(mainViewModel to hash)
    }

    @OptIn(UnstableApi::class)
    fun start() {
        if (currentJob?.isActive == true) return

        currentJob = scope.launch {
            for ((vm, hash) in queue) {
                val existingJob = downloadJobs[hash]
                if (existingJob != null) {
                    if (!existingJob.isActive) {
                        downloadJobs.remove(hash)
                    } else {
                        continue
                    }
                }

                queuedDownloads.remove(hash)
                activeDownloads.add(hash)
                updateState()

                val job = launch {
                    try {
                        downloadAudioFile(vm, hash)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(MyApplication.globalContext, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        activeDownloads.remove(hash)
                        downloadJobs.remove(hash)
                        updateState()
                    }
                }

                downloadJobs[hash] = job
                job.join()
            }
        }
    }
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        activeDownloads.clear()
        queuedDownloads.clear()

        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()

        updateState()
    }

    fun cancel(hash: String) {
        if (queuedDownloads.remove(hash)) updateState()
        downloadJobs.remove(hash)?.cancel(CancellationException("User cancelled"))
    }

    fun clearQueue() {
        queuedDownloads.clear()
        updateState()
    }

    private fun updateState() {
        _state.value = DownloadState(
            active = activeDownloads.toList(),
            queued = queuedDownloads.toList()
        )
    }

    @OptIn(
        UnstableApi::class
    )
    fun deleteDownloadedAudioFile(
        mainViewModel: PlayerViewModel,
        hashToDownload: String,
        cancelActiveDownload: Boolean = true
    ) {
        val song = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: return

        if (cancelActiveDownload) {
            downloadJobs.remove(hashToDownload)?.let { job ->
                try { job.cancel(CancellationException("User requested delete")) } catch (_: Throwable) {}
            }
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val ctx = MyApplication.globalContext
                val filesToTry = mutableListOf<File>()
                var anyDeleted = false

                filesToTry.forEach { f ->
                    if (f.exists()) {
                        try {
                            if (f.delete()) {
                                anyDeleted = true
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Files.deleteIfExists(f.toPath())
                                    anyDeleted = true
                                } else {
                                    val tmp = File(f.parentFile, "${f.name}.deleted")
                                    if (f.renameTo(tmp)) tmp.delete().also { anyDeleted = true }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }

                song.fileUri?.let { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        val rows = ctx.contentResolver.delete(uri, null, null)
                        if (rows > 0) anyDeleted = true
                    } catch (_: Exception) { }
                }

                song.art_local_link?.let { coverPath ->
                    try {
                        val coverFile = File(coverPath)
                        if (coverFile.exists()) {
                            if (!coverFile.delete()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Files.deleteIfExists(coverFile.toPath())
                                } else {
                                    val tmp = File(coverFile.parentFile, "${coverFile.name}.deleted")
                                    if (coverFile.renameTo(tmp)) tmp.delete()
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                // 🔹 Обновляем стейт
                withContext(NonCancellable + Dispatchers.Main) {
                    mainViewModel.updateFileForSong(song.link, null)
                    mainViewModel.uiState.value.allAudioData[song.link]?.art_local_link = null
                    mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))
                    mainViewModel.removeSongFromAudioSource(song.link, "Downloaded")
                    mainViewModel.updateDownloadingProgressForSong(song.link, 0f)
                    mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashToDownload]!!)
                    mainViewModel.saveAudioSourcesToRoom()
                }

                if (!anyDeleted) {
                    runOnMain {
                        Toast.makeText(ctx, "Не удалось удалить файл (возможно, он используется)", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(MyApplication.globalContext, "Ошибка при удалении: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}