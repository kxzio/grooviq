package com.example.groviq.backEnd.streamProcessor.innerTubeMine

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.IOException

fun getBestAudioStreamUrl(videoUrl: String): String? {
    return try {
        val extractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage() // важный шаг!

        val bestAudio: AudioStream? = extractor.audioStreams
            .maxByOrNull { it.averageBitrate }

        bestAudio?.url
    } catch (e: ExtractionException) {
        e.printStackTrace()
        null
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}