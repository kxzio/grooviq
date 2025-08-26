package com.example.groviq.backEnd.saveSystem

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.graphics.Bitmap
import com.example.groviq.backEnd.dataStructures.audioEnterPoint
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.dataStructures.streamInfo
import com.example.groviq.backEnd.searchEngine.ArtistDto

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val link: String,
    val title: String,
    val artists: List<ArtistDto>,
    val stream: streamInfo,
    val progressStatus: songProgressStatus,
    val playingEnterPoint: audioEnterPoint,
    val duration: Long,
    val artPath: String?,
    val artLink: String?,
    val number: Int?,
    val album_original_link: String?,
    val filePath: String?,
)

@Entity(tableName = "audio_sources")
data class AudioSourceEntity(
    @PrimaryKey val key: String,
    val nameOfAudioSource: String,
    val artistsOfAudioSource: List<ArtistDto>,
    val yearOfAudioSource: String,
    val songIds: List<String>,
    val shouldBeSavedStrictly : Boolean
)
