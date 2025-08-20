package com.example.groviq.backEnd.playEngine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlinx.coroutines.*

class Preloader(private val dataSourceFactory: DataSource.Factory, private val context: Context) {


    private var preloadJob: Job? = null

    /**
     * Прогревает трек: ждёт появления данных и качает часть в кэш.
     * @param mediaItem трек
     * @param bytesToRead сколько прогревать (по умолчанию 256 KB)
     * @param timeoutMs сколько максимум ждать открытия потока
     * @return true если прогрев удался, false иначе
     */
    suspend fun preload(
        mediaItem: MediaItem,
        bytesToRead: Int = 256 * 1024,
        timeoutMs: Long = 10_000
    ): Boolean = withContext(Dispatchers.IO) {
        preloadJob?.cancelAndJoin() // отменяем предыдущий прогрев
        preloadJob = launch {
            // будет заменено на реальный preload
        }
        try {
            withTimeout(timeoutMs) {
                doPreload(mediaItem, bytesToRead)
            }
        } catch (e: Exception) {
            println("⚠️ Preload failed: ${e.message}")
            false
        }
    }

    @OptIn(
        UnstableApi::class
    )
    suspend fun doPreload(mediaItem: MediaItem, bytesToRead: Int): Boolean =
        withContext(Dispatchers.IO) {
            val uri = mediaItem.localConfiguration?.uri ?: return@withContext false
            val dataSpec = DataSpec(uri)
            val dataSource = dataSourceFactory.createDataSource()

            try {
                var totalRead = 0
                var firstByteReceived = false

                DataSourceInputStream(dataSource, dataSpec).use { input ->
                    val buffer = ByteArray(16 * 1024)

                    while (totalRead < bytesToRead) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalRead += read

                        if (!firstByteReceived) {
                            firstByteReceived = true
                            println("✅ Stream opened for ${mediaItem.mediaId}")
                        }
                    }
                }

                println("✅ Preloaded ${mediaItem.mediaId}, ${totalRead / 1024} KB warmed")
                true
            } catch (e: IOException) {
                println("⚠️ IO error while preloading: ${e.message}")
                false
            }
        }
    fun cancel() {
        preloadJob?.cancel()
    }
}