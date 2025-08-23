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
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.groviq.MyApplication
import com.example.groviq.retryWithBackoff
import kotlinx.coroutines.*
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


fun downloadAudioFile(mainViewModel: PlayerViewModel, hashToDownload: String) {

    //cancel previous
    currentDownloading?.cancel()

    // no internet start
    if (!hasInternetConnection(
            MyApplication.globalContext!!)) return

    //take song
    val song = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: return

    //if song already in download process - exit
    if (song.progressStatus.downloadingHandled == true) return

    //setup flag
    runOnMain {
        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = true))
    }

    //start main job
    currentDownloading = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        // ensure only one downloader for a given hash at a time
        downloadMutex.withLock {
            try {

                val streamUrl = retryWithBackoff(
                    retries = 3,
                    initialDelayMs = 500L
                ) {
                    // Await stream url (твоя функция)
                    mainViewModel.awaitStreamUrlFor(hashToDownload)
                        ?: throw IOException("Stream URL is null")
                }

                if (!isActive) return@withLock

                // get head
                val headRequest = Request.Builder()
                    .url(streamUrl)
                    .head()
                    .build()

                val headCall = downloadHttpClient.newCall(headRequest)
                val headResponse = try {
                    headCall.awaitResponseCancellable()
                } catch (e: Exception) {
                    throw IOException("HEAD request failed: ${e.message}", e)
                }

                if (!headResponse.isSuccessful) {
                    headResponse.close()
                    throw IOException("HEAD failed: code ${headResponse.code}")
                }

                val contentLengthHeader = headResponse.header("Content-Length")
                val acceptRanges = headResponse.header("Accept-Ranges") // often "bytes" or null
                headResponse.close()

                val totalSize = contentLengthHeader?.toLongOrNull() ?: -1L
                val supportsRange = acceptRanges?.equals("bytes", true) == true && totalSize > 0

                //Prepare file names
                val safeAlbum = (song.album_original_link ?: "album")
                    .replace(Regex("[\\\\/:*?\"<>| ]"), "_")
                val urlHash = (song.link.takeIf { it.length >= 40 }?.substring(20, 40) ?: song.link)
                    .let { md5(it) }
                    .take(8)
                val uniqueName = "${safeAlbum}__${urlHash}.mp3"
                val partName = "$uniqueName.part"
                val finalFile = File(MyApplication.globalContext!!.getExternalFilesDir(null), uniqueName)
                val partFile = File(MyApplication.globalContext!!.getExternalFilesDir(null), partName)

                // if file already downloaded - exit
                if (finalFile.exists() && totalSize > 0 && finalFile.length() == totalSize) {
                    //already have
                    runOnMain {
                        mainViewModel.updateFileForSong(song.link, finalFile)
                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)
                        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))
                    }
                    return@withLock
                }

               // if we have partfile - resume downloading
                val existingBytes = partFile.length().coerceAtLeast(0L)

                if (existingBytes > 0 && supportsRange && existingBytes == totalSize) {
                    //rename old PART file, if it was already downloaded at all
                    partFile.renameTo(finalFile)
                    runOnMain {
                        mainViewModel.updateFileForSong(song.link, finalFile)
                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)
                        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))
                    }
                    return@withLock
                }

                //choose strategy ( range or default )
                if (supportsRange && totalSize > 0L) {
                    //resume friendly downloading
                    var downloadedSoFar = existingBytes
                    val downloadedAtomic = AtomicLong(downloadedSoFar)

                    //if we have no part file - create one
                    if (!partFile.exists()) {
                        partFile.parentFile?.mkdirs()
                        partFile.createNewFile()
                    }

                    //updating
                    var lastReportedPercent = -1
                    var lastReportTime = 0L
                    val reportIntervalMs = 300L

                    //resume downloading if we had
                    val startPosition = downloadedSoFar
                    val rangeHeader = "bytes=$startPosition-${totalSize - 1}"

                    val rangeRequest = Request.Builder()
                        .url(streamUrl)
                        .addHeader("Range", rangeHeader)
                        .build()

                    val rangeCall = downloadHttpClient.newCall(rangeRequest)

                    //download with retries
                    retryWithBackoff(retries = 3, initialDelayMs = 400L) {
                        if (!isActive) throw CancellationException()

                        val response = try {
                            rangeCall.awaitResponseCancellable()
                        } catch (e: Exception) {
                            throw IOException("Range request failed: ${e.message}", e)
                        }

                        if (response.code == 416) {
                            // Requested range not satisfiable
                            response.close()
                            return@retryWithBackoff
                        }

                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            throw IOException("Range failed: code $code")
                        }

                        val body = response.body ?: run {
                            response.close()
                            throw IOException("Empty body for range")
                        }

                        // Read stream and append to partFile at startPosition
                        // Important: use RandomAccessFile to seek to startPosition and write bytes
                        RandomAccessFile(partFile, "rw").use { raf ->
                            raf.seek(startPosition)
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var lastReportedPercent = -1
                                var lastReportTime = 0L

                                while (isActive) {
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break

                                    raf.write(buffer, 0, bytesRead)
                                    val newTotal = downloadedAtomic.addAndGet(bytesRead.toLong())

                                    //updating
                                    if (totalSize > 0) {
                                        val percent = ((newTotal * 100) / totalSize).toInt()
                                        val now = System.currentTimeMillis()
                                        if (percent != lastReportedPercent && (now - lastReportTime >= reportIntervalMs)) {
                                            lastReportedPercent = percent
                                            lastReportTime = now
                                            runOnMain {
                                                mainViewModel.updateDownloadingProgressForSong(song.link, percent.toFloat())
                                            }
                                        }
                                    }

                                    // check safe
                                    if (!isActive) break
                                }
                            }
                        }


                        response.close()
                    }

                    if (!isActive) throw CancellationException()

                    //check size and rename if we have part file
                    if (partFile.exists() && (totalSize < 0 || partFile.length() >= totalSize)) {
                        partFile.renameTo(finalFile)
                    }

                    //final updates
                    runOnMain {
                        val downloadedFile = if (finalFile.exists()) finalFile else partFile

                        mainViewModel.updateFileForSong(song.link, downloadedFile)

                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)

                        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))

                        mainViewModel.addSongToAudioSource(song.link, "Скачанное")

                        val toSave = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: song
                        mainViewModel.saveSongToRoom(toSave)
                        mainViewModel.saveAudioSourcesToRoom()
                    }

                } else {
                    // SERVER DOES NOT SUPPORT RANGE (или totalSize unknown) -> full download (overwrite .part)
                    partFile.parentFile?.mkdirs()
                    if (partFile.exists()) partFile.delete()
                    partFile.createNewFile()

                    val getRequest = Request.Builder().url(streamUrl).build()
                    val getCall = downloadHttpClient.newCall(getRequest)

                    retryWithBackoff(retries = 3, initialDelayMs = 400L) {
                        if (!isActive) throw CancellationException()

                        val response = try {
                            getCall.awaitResponseCancellable()
                        } catch (e: Exception) {
                            throw IOException("GET failed: ${e.message}", e)
                        }

                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            throw IOException("GET failed: code $code")
                        }

                        val body = response.body ?: run {
                            response.close()
                            throw IOException("Empty body")
                        }

                        // записываем прямо в .part
                        val bufferedSink = partFile.sink().buffer()
                        val source = body.source()
                        try {
                            source.use { src ->
                                bufferedSink.use { bs ->
                                    var totalRead = 0L
                                    val bufferSize = 64L * 1024L
                                    var lastPercent = -1
                                    var lastReportTime2 = 0L
                                    val reportIntervalMs2 = 300L

                                    while (isActive) {
                                        val read = try {
                                            src.read(bs.buffer(), bufferSize)
                                        } catch (e: IOException) {
                                            //retry
                                            throw e
                                        }

                                        if (read == -1L) break

                                        totalRead += read
                                        bs.emit() // flush to file

                                        if (totalSize > 0L) {
                                            val percent = ((totalRead * 100) / totalSize).toInt()
                                            val now = System.currentTimeMillis()
                                            if (percent != lastPercent && (now - lastReportTime2 >= reportIntervalMs2)) {
                                                lastPercent = percent
                                                lastReportTime2 = now
                                                runOnMain {
                                                    mainViewModel.updateDownloadingProgressForSong(song.link, percent.toFloat())
                                                }
                                            }
                                        }

                                        //check safe
                                        if (!isActive) break
                                    }
                                }
                            }
                        } finally {
                            //close response
                            try { response.close() } catch (_: Throwable) {}
                        }
                    }

                    if (!isActive) throw CancellationException()

                    //rename part file to final
                    if (partFile.exists()) partFile.renameTo(finalFile)

                    runOnMain {
                        val downloadedFile = if (finalFile.exists()) finalFile else partFile

                        mainViewModel.updateFileForSong(song.link, downloadedFile)

                        mainViewModel.updateDownloadingProgressForSong(song.link, 100f)

                        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))

                        mainViewModel.addSongToAudioSource(song.link, "Скачанное")

                        val toSave = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: song
                        mainViewModel.saveSongToRoom(toSave)
                        mainViewModel.saveAudioSourcesToRoom()
                    }
                }

            } catch (e: CancellationException) {
                //thread canceled
            } catch (e: Exception) {
                //show error
                runOnMain {
                    Toast.makeText(MyApplication.globalContext, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // CANCEL flag of downloading
                withContext(NonCancellable + Dispatchers.Main) {
                    val latest = mainViewModel.uiState.value.allAudioData[hashToDownload]
                    if (latest != null && latest.progressStatus.downloadingHandled == true) {
                        mainViewModel.updateStatusForSong(latest.link, latest.progressStatus.copy(downloadingHandled = false))
                    }
                }
            }
        } // end mutex
    } // end launch
}

fun runOnMain(block: () -> Unit) {
    if (Looper.getMainLooper().thread == Thread.currentThread()) {
        block()
    } else {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }
}


val downloadJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

fun deleteDownloadedAudioFile(
    mainViewModel: PlayerViewModel,
    hashToDownload: String,
    cancelActiveDownload: Boolean = true
) {
    val song = mainViewModel.uiState.value.allAudioData[hashToDownload] ?: return

    //cancel if we already downloading
    if (cancelActiveDownload) {
        downloadJobs.remove(hashToDownload)?.let { job ->
            try { job.cancel(CancellationException("User requested delete")) } catch (_: Throwable) {}
        }
    }

    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        try {
            //get final file or part file, if we had one
            val docFile = song.file
            val filesToTry = mutableListOf<File>()
            if (docFile != null) {
                filesToTry.add(docFile)
                //part files
                filesToTry.add(File(docFile.parentFile, "${docFile.name}.part"))
                //additional check
                filesToTry.add(File(docFile.parentFile, "${docFile.nameWithoutExtension}.mp3.part"))
            }

            var anyDeleted = false
            filesToTry.forEach { f ->
                if (f.exists()) {
                    try {
                       //defaut delete
                        if (f.delete()) {
                            anyDeleted = true
                        } else {
                            //try atomic delete if default delete is not availabl
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Files.deleteIfExists(f.toPath())
                                    anyDeleted = true
                                } else {
                                    //FALBACK - rename to deleted and delete
                                    val tmp = File(f.parentFile, "${f.name}.deleted")
                                    if (f.renameTo(tmp)) {
                                        tmp.delete()
                                        anyDeleted = true
                                    }
                                }
                            } catch (ex: Exception) {

                            }
                        }
                    } catch (e: Exception) {

                    }
                }
            }

            //downloading from mediastore
            try {
                val ctx = MyApplication.globalContext ?: applicationContextPlaceholder()
                docFile?.let { f ->
                    //delete from mediastore
                    deleteFromMediaStoreIfExists(ctx, f)
                }
            } catch (e: Exception) {
            }

            //delete from Db and update ui
            withContext(NonCancellable + Dispatchers.Main) {
                mainViewModel.updateFileForSong(song.link, null)

                mainViewModel.updateStatusForSong(
                    song.link,
                    song.progressStatus.copy(downloadingHandled = false)
                )

                mainViewModel.removeSongFromAudioSource(song.link, "Скачанное")

                mainViewModel.updateDownloadingProgressForSong(song.link, 0f)

                mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashToDownload]!!)
                mainViewModel.saveAudioSourcesToRoom()
            }

            if (!anyDeleted) {
                //error. show it on ui
                runOnMain {
                    Toast.makeText(MyApplication.globalContext, "Не удалось удалить файл (возможно, он используется)", Toast.LENGTH_LONG).show()
                }
            }

        } catch (e: CancellationException) {
            //cancel
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(MyApplication.globalContext, "Ошибка при удалении: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
