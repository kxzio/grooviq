package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap
import com.example.groviq.backEnd.searchEngine.ArtistDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    var number : Int? = null,

    //it helps to easily move to original album in browse
    var album_original_link : String = "",

)
{
    fun shouldGetStream(ttlMillis: Long = TimeUnit.HOURS.toMillis(5)): Boolean {
        if (stream.streamUrl.isNullOrEmpty()) return true
        val now = System.currentTimeMillis()
        return now - stream.setTime >= ttlMillis
    }
}

data class CurrentSongTimeProgress(
    val progress: Float,
    val position: Long
)

// current song progress state
private val _progressState = MutableStateFlow(CurrentSongTimeProgress(0f, 0L))
val songProgressState: StateFlow<CurrentSongTimeProgress> = _progressState

fun setSongProgress(progress: Float, position: Long) {
    _progressState.value = CurrentSongTimeProgress(progress, position)
}