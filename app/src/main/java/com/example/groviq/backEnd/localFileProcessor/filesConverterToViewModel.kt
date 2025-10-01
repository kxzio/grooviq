package com.example.groviq.backEnd.localFileProcessor

import com.example.groviq.backEnd.dataStructures.ViewFolder

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.searchEngine.ArtistDto
import java.io.File

data class metadataForLocalSongs(
    val title : String,
    val album : String,
    val artistDto: List<ArtistDto>,
    val year : String,

)

@OptIn(
    UnstableApi::class
)
fun extractMetadataFromFile(uri: Uri): metadataForLocalSongs {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(MyApplication.globalContext, uri)
    val title  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown"
    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
    val album  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
    val year   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "Unknown Year"
    retriever.release()

    val artistDto = ArtistDto(title = artist, imageUrl = "", albums = emptyList())
    return metadataForLocalSongs(
        title = title,
        album = album,
        artistDto = listOf(artistDto),
        year = year
    )
}


