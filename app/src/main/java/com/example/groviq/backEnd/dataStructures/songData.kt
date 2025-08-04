package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap
import com.example.groviq.backEnd.searchEngine.ArtistDto
import java.util.concurrent.TimeUnit

enum class audioEnterPoint {
    STREAM,
    FILE,
    NOT_PLAYABLE,
    NOT_AVAILABLE
}

data class songProgressStatus(
    var streamHandled      : Boolean = false,
    var downloadingHandled : Boolean = false
)

data class streamInfo(
    var streamUrl : String = "",
    var setTime   : Long   = 0L
)


data class songData(

    //KEY
    var link: String = "",

    var title  : String = "",

    var artists: List<ArtistDto> = emptyList(),

    //stream that app uses for playing not saved audio
    var stream : streamInfo = streamInfo(),

    //shows if thread already getting the stream or downloading song
    var progressStatus : songProgressStatus = songProgressStatus(),

    //shows which audio should we use, stream, file, or should we ignore this file
    var playingEnterPoint : audioEnterPoint = audioEnterPoint.NOT_PLAYABLE,

    //duration of song
    var duration: Long = 0L,

    //album cover
    var art    : Bitmap? = null,

    //number of song
    var number : Int? = null

)
{
    fun shouldGetStream(ttlMillis: Long = TimeUnit.HOURS.toMillis(5)): Boolean {
        if (stream.streamUrl.isNullOrEmpty()) return true
        val now = System.currentTimeMillis()
        return now - stream.setTime >= ttlMillis
    }
}