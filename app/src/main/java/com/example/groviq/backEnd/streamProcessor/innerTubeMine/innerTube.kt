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

        if (true) {
            val bestVideo = videoStreams
                .filter {
                    (it.height ?: 0) >= 360 &&
                            (it.bitrate ?: 0) > 200_000 &&
                            (it.fps ?: 0) >= 15
                }
                .maxByOrNull { it.bitrate ?: 0 }

            if (bestVideo != null)
                return Pair(bestVideo.url, true)
        }

        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.firstOrNull()
        Pair(bestAudio?.url, false)

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
