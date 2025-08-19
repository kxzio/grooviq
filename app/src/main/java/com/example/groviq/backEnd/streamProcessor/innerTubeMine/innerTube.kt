package com.example.groviq.backEnd.streamProcessor.innerTubeMine

import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.IOException

fun getBestAudioStreamUrl(videoUrl: String): String? {
    return try {
        // ServiceList.YouTube.getStreamExtractor
        var extractor: StreamExtractor? = null
        runBlocking { /* nothing - placeholder to keep nested structure; we'll use blocking-free approach below*/ }
        val ext = ServiceList.YouTube.getStreamExtractor(videoUrl)
        ext.fetchPage()
        val bestAudio = ext.audioStreams.maxByOrNull { it.averageBitrate }
        bestAudio?.url
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