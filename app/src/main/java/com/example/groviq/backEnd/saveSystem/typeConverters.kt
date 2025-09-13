package com.example.groviq.backEnd.saveSystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import java.security.MessageDigest


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

fun encodeYouTubeMusic(link: String): String? {

    if (link.isNullOrEmpty()) return null

    val uri = Uri.parse(link)
    val v = uri.getQueryParameter("v")
    val list = uri.getQueryParameter("list")
    val path = uri.path ?: ""

    return when {
        !v.isNullOrEmpty() && !list.isNullOrEmpty() -> "A_${v}_${list}"
        !v.isNullOrEmpty() -> "V_${v}"
        !list.isNullOrEmpty() -> "P_${list}"
        path.contains("/channel/") -> "C_${path.substringAfterLast("/")}"
        else -> throw IllegalArgumentException("Unsupported YouTube Music link")
    }
}

fun decodeYouTubeMusic(encoded: String): String {
    val parts = encoded.split("_")
    return when (parts[0]) {
        "V" -> "https://music.youtube.com/watch?v=${parts[1]}"
        "A" -> "https://music.youtube.com/watch?v=${parts[1]}&list=${parts[2]}"
        "P" -> "https://music.youtube.com/playlist?list=${parts[1]}"
        "C" -> "https://music.youtube.com/channel/${parts[1]}"
        else -> throw IllegalArgumentException("Invalid encoded format")
    }
}
fun safeDecodeYouTubeMusic(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith("V_") || link.startsWith("A_") || link.startsWith("P_") || link.startsWith("C_")) {
        decodeYouTubeMusic(link)
    } else {
        link
    }
}



fun songData.toEntity(context: Context): SongEntity {

    val filePath = this.file?.let { file ->
        file.absolutePath
    }

    return SongEntity(
        link = encodeYouTubeMusic(this.link) ?: "",
        title = this.title,
        artists = this.artists,
        stream = this.stream,
        progressStatus = this.progressStatus,
        playingEnterPoint = this.playingEnterPoint,
        duration = this.duration,
        artPath = this.art_local_link,
        number = this.number,
        album_original_link = encodeYouTubeMusic(this.album_original_link),
        filePath = filePath,
        artLink = this.art_link
    )
}

fun SongEntity.toDomain(context: Context): songData {

    val file = this.filePath?.let { path ->
        val f = File(path)
        if (f.exists()) f else null
    }

    return songData(
        link = safeDecodeYouTubeMusic(this.link) ?: "",
        title = this.title,
        artists = this.artists,
        stream = this.stream,
        progressStatus = this.progressStatus,
        playingEnterPoint = this.playingEnterPoint,
        duration = this.duration,
        art_local_link = this.artPath,
        number = this.number,
        album_original_link = safeDecodeYouTubeMusic(this.album_original_link) ?: "",
        file = file,
        art_link = this.artLink ?: ""
    )
}

fun audioSource.toEntity(key: String): AudioSourceEntity = AudioSourceEntity(
    key = key,
    nameOfAudioSource = this.nameOfAudioSource,
    artistsOfAudioSource = this.artistsOfAudioSource,
    yearOfAudioSource = this.yearOfAudioSource,
    songIds = this.songIds.mapNotNull { encodeYouTubeMusic(it) },
    shouldBeSavedStrictly = this.shouldBeSavedStrictly,
    autoGenerated = this.autoGenerated,
    timeUpdate = this.timeUpdate
)

fun AudioSourceEntity.toDomain(): audioSource = audioSource(
    nameOfAudioSource = this.nameOfAudioSource,
    artistsOfAudioSource = this.artistsOfAudioSource,
    yearOfAudioSource = this.yearOfAudioSource,
    songIds = this.songIds.mapNotNull { safeDecodeYouTubeMusic(it) }.toMutableList(),
    shouldBeSavedStrictly = this.shouldBeSavedStrictly,
    autoGenerated = this.autoGenerated,
    timeUpdate = this.timeUpdate
)