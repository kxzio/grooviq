package com.example.groviq.backEnd.streamProcessor

import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.globalContext
import com.example.groviq.hasInternetConnection
import com.example.groviq.isSmall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

fun md5(input: String): String {
    val bytes = MessageDigest
        .getInstance("MD5")
        .digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

//album getter with one job active
var currentDownloading: Job? = null




fun downloadAudioFile(mainViewModel: PlayerViewModel, hashToDownload : String) {

        currentDownloading?.cancel()

        if (!hasInternetConnection(globalContext!!)) {
            return
        }

        val song = mainViewModel.uiState.value.allAudioData[hashToDownload]

        if (song == null)
            return

        if (mainViewModel.uiState.value.allAudioData[hashToDownload]!!.progressStatus.downloadingHandled == true)
            return

        mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = true))

        currentDownloading = CoroutineScope(Dispatchers.IO).launch {

            try {

                if (song!!.shouldGetStream()) {

                    if (song.progressStatus.streamHandled.not()) {
                        //stream not handled yet by any thread, we should get it by yourself

                        //clear old stream if we had one
                        mainViewModel.updateStreamForSong(hashToDownload, "")
                        //request to get the new one
                        fetchAudioStream(mainViewModel, hashToDownload)
                    }

                }

                //update image if size is small
                if (song.art != null)
                {
                    if (song.art!!.isSmall(200, 200))
                    {
                        fetchNewImage(mainViewModel, song.link)
                    }
                }
                else
                    fetchNewImage(mainViewModel, song.link)

                //reactive waiting for stream url
                val streamUrl = mainViewModel.awaitStreamUrlFor(hashToDownload)

                // 2) Делаем HEAD-запрос, чтобы узнать Content-Length (длину)
                val client = OkHttpClient()
                val headRequest = Request.Builder()
                    .url(streamUrl!!)
                    .head()
                    .build()
                val headResponse = client.newCall(headRequest).execute()
                if (!headResponse.isSuccessful) {
                    headResponse.close()
                    throw IOException("Ошибка HEAD-запроса: код ${headResponse.code}")
                }

                val contentLengthHeader = headResponse.header("Content-Length")
                headResponse.close()

                val totalSize = contentLengthHeader?.toLongOrNull() ?: -1L

                // Готовим имя файла
                val safeAlbum = (song.album_original_link ?: "album")
                    .replace(Regex("[\\\\/:*?\"<>| ]"), "_")
                val urlHash = song.link
                    .substring(20, 40)
                    .let { md5(it ?: "") }
                    ?.take(8)
                val uniqueName = "${safeAlbum}_${"_"}_$urlHash.mp3"

                val outputFile = File(globalContext!!.getExternalFilesDir(null), uniqueName)
                if (outputFile.exists()) outputFile.delete()

                if (totalSize <= 0) {
                    val request = Request.Builder().url(streamUrl).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        throw IOException("Ошибка загрузки: код ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Пустое тело ответа")
                    val inputStream = body.byteStream()
                    val output = FileOutputStream(outputFile)
                    val bufferSize = 64 * 1024  // 64 КБ
                    val buffer = ByteArray(bufferSize)
                    var downloadedBytes = 0L
                    var readCount: Int

                    while (inputStream.read(buffer).also { readCount = it } != -1) {
                        output.write(buffer, 0, readCount)
                        downloadedBytes += readCount
                        if (totalSize > 0) {
                            val progress = (downloadedBytes * 100 / totalSize).toInt()
                            mainViewModel.updateDownloadingProgressForSong(song.link, progress.toFloat())
                        }
                    }

                    output.flush()
                    output.close()
                    inputStream.close()
                    response.close()

                } else {
                    // 4) Если длина известна, разбиваем на 4 диапазона
                    val parts = 4
                    val partSize = totalSize / parts
                    val downloadedBytes = AtomicLong(0L)

                    // Создаем файл нужного размера
                    RandomAccessFile(outputFile, "rw").use { raf ->
                        raf.setLength(totalSize)
                    }

                    // Запускаем параллельные скачивания для каждого диапазона
                    coroutineScope {
                        val deferreds = (0 until parts).map { partIndex ->
                            async(
                                Dispatchers.IO) {
                                val start = partIndex * partSize
                                val end = if (partIndex == parts - 1) {
                                    totalSize - 1
                                } else {
                                    start + partSize - 1
                                }
                                val rangeHeader = "bytes=$start-$end"
                                val rangeRequest = Request.Builder()
                                    .url(streamUrl)
                                    .addHeader("Range", rangeHeader)
                                    .build()
                                client.newCall(rangeRequest).execute().use { resp ->
                                    if (resp.code == 416) {
                                        // Запрошенный диапазон вне файла, игнорируем
                                        return@async
                                    }
                                    if (!resp.isSuccessful) {
                                        throw IOException("Range Ошибка: код ${resp.code}")
                                    }
                                    val body = resp.body ?: throw IOException("Пустое тело Range")
                                    val input = body.byteStream()
                                    RandomAccessFile(outputFile, "rw").use { raf ->
                                        raf.seek(start)
                                        val bufferSizePart = 64 * 1024 // 64 КБ
                                        val bufferPart = ByteArray(bufferSizePart)
                                        var bytesRead: Int
                                        while (input.read(bufferPart).also { bytesRead = it } != -1) {
                                            raf.write(bufferPart, 0, bytesRead)
                                            val newTotal = downloadedBytes.addAndGet(bytesRead.toLong())
                                            val progress = (newTotal * 100 / totalSize).toInt()
                                            mainViewModel.updateDownloadingProgressForSong(song.link, progress.toFloat())
                                        }
                                    }
                                    input.close()
                                }
                            }
                        }
                        deferreds.awaitAll()
                    }
                }


                // 5) После успешного скачивания, переключаемся в Main и обновляем состояние
                withContext(Dispatchers.Main) {

                    mainViewModel.updateFileForSong(
                        song.link,
                        outputFile
                    )

                    mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))

                    mainViewModel.addSongToAudioSource(
                        song.link,
                        "Скачанное"
                    )

                    mainViewModel.updateDownloadingProgressForSong(song.link, 100f)

                    mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashToDownload]!!)
                    mainViewModel.saveAudioSourcesToRoom()

                }
            } catch (e: Exception) {
                withContext(
                    Dispatchers.Main) {
                    Toast.makeText(
                        globalContext,
                        "${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

fun deleteDownloadedAudioFile(mainViewModel: PlayerViewModel, hashToDownload : String
) {

        val song = mainViewModel.uiState.value.allAudioData[hashToDownload]

        if (song == null)
            return

        CoroutineScope(
            Dispatchers.IO).launch {
            try {

                val docFile = song.file
                if (docFile != null && docFile.exists()) {
                    docFile.delete()
                }

                withContext(Dispatchers.Main) {
                    mainViewModel.updateFileForSong(
                        song.link,
                        null
                    )

                    mainViewModel.updateStatusForSong(song.link, song.progressStatus.copy(downloadingHandled = false))

                    mainViewModel.removeSongFromAudioSource(
                        song.link,
                        "Скачанное"
                    )

                    mainViewModel.updateDownloadingProgressForSong(song.link, 0f)

                    mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashToDownload]!!)
                    mainViewModel.saveAudioSourcesToRoom()

                }
            } catch (e: Exception) {
                withContext(
                    Dispatchers.Main) {

                }
            }
        }
    }
