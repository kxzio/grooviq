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

suspend fun getBestAudioStreamUrl(videoUrl: String): String? {
    val maxAttempts = 7
    repeat(maxAttempts) { attempt ->
        try {
            val result = withContext(Dispatchers.IO) {
                withTimeout(15_000L) {
                    extractAudioStreamSafe(videoUrl)
                }
            }

            val normalized = result?.trim()
            if (normalized.isNullOrEmpty() || normalized.equals("null", true) || normalized.equals("n/a", true)) {
                throw IOException("Empty or invalid stream URL")
            }

            return normalized
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

private fun extractAudioStreamSafe(videoUrl: String): String? {
    return try {
        val extractor: StreamExtractor =
            ServiceList.YouTube.getStreamExtractor(videoUrl).apply { fetchPage() }
        extractor.audioStreams.maxByOrNull { it.averageBitrate }?.url
            ?: extractor.audioStreams.firstOrNull()?.url
    } catch (e: ExtractionException) {
        e.printStackTrace()
        null
    } catch (e: IOException) {
        e.printStackTrace()
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}