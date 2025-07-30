package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap

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

data class songData(

    var title  : String = "",
    var author : String = "",

    //stream that app uses for playing not saved audio
    var stream : String = "",

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