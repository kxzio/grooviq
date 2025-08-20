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

var preloadedTracks = mutableSetOf<String>()

class Preloader(private val dataSourceFactory: DataSource.Factory, private val context: Context) {


    private var preloadJob: Job? = null

    suspend fun preload(mediaItem: MediaItem, bytesToRead: Int = 256 * 1024): Boolean {
        if (!preloadedTracks.add(mediaItem.mediaId ?: "")) {
            println("🔹 Already preloaded: ${mediaItem.mediaId}")
            return true
        }

        return doPreload(mediaItem, bytesToRead)
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