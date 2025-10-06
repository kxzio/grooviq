package com.example.groviq.backEnd.streamProcessor.innerTubeMine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

suspend fun getBestAudioStreamUrl(videoUrl: String): Pair<String?, Boolean>? {
    val maxAttempts = 7
    repeat(maxAttempts) { attempt ->
        try {
            val result = withContext(Dispatchers.IO) {
                withTimeout(15_000L) {
                    extractStreamSafe(videoUrl)
                }
            }

            val normalized = result?.first?.trim()
            if (normalized.isNullOrEmpty() || normalized.equals("null", true) || normalized.equals("n/a", true)) {
                throw IOException("Empty or invalid stream URL")
            }

            return Pair(normalized, result.second)

        } catch (e: Exception) {
            println("getBestAudioStreamUrl attempt ${attempt + 1} failed: ${e.message}")

            if (attempt < maxAttempts - 1) {
                val base = 1000L * (1L shl attempt) // 1s,2s,4s,8s,...
                val capped = min(base, 10_000L)     // cap 10s - LIMIT FOR MAX WAITING TIME
                val jitter = Random.nextLong(0, 500L)
                delay(capped + jitter)
            }
        }
    }
    return null
}

private fun extractStreamSafe(videoUrl: String): Pair<String?, Boolean>? {
    return try {
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl).apply { fetchPage() }

        val videoStreams = extractor.videoStreams
        val audioStreams = extractor.audioStreams

        // Если есть видеопотоки — проверяем, что это реально клип
        if (!videoStreams.isNullOrEmpty()) {
            val hlsUrl = extractor.hlsUrl
            if (!hlsUrl.isNullOrEmpty()) {
                // Это клип с аудио
                return Pair(hlsUrl, true)
            }

            // Если нет HLS, но есть нормальные видео-потоки — считаем клипом
            val bestVideo = videoStreams.maxByOrNull { it.bitrate }
            if (bestVideo != null && bestVideo.bitrate > 200_000) { // порог для фильтра автогенерации
                return Pair(bestVideo.url, true)
            }
        }

        // Если видео-потоков нет или они мелкие — возвращаем просто аудио
        Pair(audioStreams.maxByOrNull { it.averageBitrate }?.url
            ?: audioStreams.firstOrNull()?.url, false)

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
