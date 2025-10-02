package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.searchEngine.ArtistDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.TimeUnit

enum class audioEnterPoint {
    STREAM,
    FILE,
    NOT_PLAYABLE,
    NOT_AVAILABLE
}

data class songProgressStatus(
    var streamHandled      : Boolean = false,
    var downloadingHandled : Boolean = false,
    var downloadingProgress: Float = 0f
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

    //album cover - straight link version
    var art_link : String? = null,

    //album cover - straight link to path to file
    var art_local_link : String? = null,

    //number of song
    var number : Int? = null,

    //it helps to easily move to original album in browse
    var album_original_link : String = "",

    //file downloaded
    var fileUri: String? = null,

    //USING YEAR ONLY FOR LOCAL FILES, cause audiosource dont cahce year in local files
    var year : String = "",

    var isExternal : Boolean = false

    )
{
    private val existenceCache = mutableMapOf<String, Boolean>()

    fun localExists(): Boolean {
        val pathOrUri = fileUri ?: return false

        // кэш
        existenceCache[pathOrUri]?.let { return it }

        val exists = try {
            if (pathOrUri.startsWith("/")) {
                File(pathOrUri).exists()
            } else {
                val uri = Uri.parse(pathOrUri)
                val docFile = DocumentFile.fromSingleUri(MyApplication.globalContext, uri)
                docFile?.exists() ?: false
            }
        } catch (e: Exception) {
            false
        }

        // сохраняем результат в кэш
        existenceCache[pathOrUri] = exists
        return exists
    }

    fun shouldGetStream(ttlMillis: Long = TimeUnit.HOURS.toMillis(5)): Boolean {

        if (localExists()) return false

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