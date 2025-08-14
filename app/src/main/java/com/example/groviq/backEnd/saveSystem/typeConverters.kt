package com.example.groviq.backEnd.saveSystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import com.example.groviq.backEnd.dataStructures.audioEnterPoint
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.dataStructures.streamInfo
import com.example.groviq.backEnd.searchEngine.ArtistDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class Converters {
    private val gson = Gson()

    // ---- ArtistDto list ----
    @TypeConverter
    fun fromArtistList(value: List<ArtistDto>?): String? =
        if (value == null) null else gson.toJson(value)

    @TypeConverter
    fun toArtistList(value: String?): List< ArtistDto> =
        if (value.isNullOrEmpty()) emptyList()
        else gson.fromJson(value, object : TypeToken<List< ArtistDto>>() {}.type)

    // ---- List<String> (songIds) ----
    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringList(json: String?): List<String> =
        if (json.isNullOrEmpty()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<String>>() {}.type)

    // ---- streamInfo ----
    @TypeConverter
    fun fromStreamInfo(s: streamInfo?): String? = gson.toJson(s)
    @TypeConverter
    fun toStreamInfo(json: String?): streamInfo = if (json.isNullOrEmpty()) streamInfo() else gson.fromJson(json, streamInfo::class.java)

    // ---- songProgressStatus ----
    @TypeConverter
    fun fromProgressStatus(s: songProgressStatus?): String? = gson.toJson(s)
    @TypeConverter
    fun toProgressStatus(json: String?): songProgressStatus = if (json.isNullOrEmpty()) songProgressStatus() else gson.fromJson(json, songProgressStatus::class.java)

    // ---- audioEnterPoint enum ----
    @TypeConverter
    fun fromAudioEnterPoint(e: audioEnterPoint?): String? = e?.name
    @TypeConverter
    fun toAudioEnterPoint(name: String?): audioEnterPoint = if (name == null) audioEnterPoint.NOT_PLAYABLE else audioEnterPoint.valueOf(name)


}
fun sanitizeFileName(input: String): String {
    return input.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, filenameKey: String): String {

    val safeFileName = "cover_" + sanitizeFileName(filenameKey) + ".png"
    val file = File(context.filesDir, safeFileName)

    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
    }

    return file.absolutePath
}

fun loadBitmapFromInternalStorage(context: Context, path: String): Bitmap? {
    val file = File(path)
    return if (file.exists()) {
        BitmapFactory.decodeFile(file.absolutePath)
    } else null
}

fun songData.toEntity(context: Context): SongEntity {
    val artFilePath = this.art?.let { bitmap ->
        saveBitmapToInternalStorage(context, bitmap, this.link)
    }

    return SongEntity(
        link = this.link,
        title = this.title,
        artists = this.artists,
        stream = this.stream,
        progressStatus = this.progressStatus,
        playingEnterPoint = this.playingEnterPoint,
        duration = this.duration,
        artPath = artFilePath,
        number = this.number,
        album_original_link = this.album_original_link
    )
}


fun SongEntity.toDomain(context: Context): songData {
    val bitmap = this.artPath?.let { path ->
        loadBitmapFromInternalStorage(context, path)
    }

    return songData(
        link = this.link,
        title = this.title,
        artists = this.artists,
        stream = this.stream,
        progressStatus = this.progressStatus,
        playingEnterPoint = this.playingEnterPoint,
        duration = this.duration,
        art = bitmap,
        number = this.number,
        album_original_link = this.album_original_link ?: ""
    )
}

fun audioSource.toEntity(key: String): AudioSourceEntity = AudioSourceEntity(
    key = key,
    nameOfAudioSource = this.nameOfAudioSource,
    artistsOfAudioSource = this.artistsOfAudioSource,
    yearOfAudioSource = this.yearOfAudioSource,
    songIds = this.songIds
)

fun AudioSourceEntity.toDomain(): audioSource = audioSource(
    nameOfAudioSource = this.nameOfAudioSource,
    artistsOfAudioSource = this.artistsOfAudioSource,
    yearOfAudioSource = this.yearOfAudioSource,
    songIds = this.songIds.toMutableList()
)