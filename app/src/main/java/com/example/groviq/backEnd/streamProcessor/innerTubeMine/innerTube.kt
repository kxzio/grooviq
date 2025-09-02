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

suspend fun getBestAudioStreamUrl(videoUrl: String): String? {
    repeat(5) { attempt ->
        try {
            return withContext(Dispatchers.IO) {
                withTimeout(15_000L) {
                    extractAudioStreamSafe(videoUrl)
                }
            }
        } catch (e: Exception) {
            println("getBestAudioStreamUrl attempt ${attempt + 1} failed: ${e.message}")
            delay((attempt + 1) * 1000L)
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