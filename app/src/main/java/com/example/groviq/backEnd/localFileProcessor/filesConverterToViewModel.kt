package com.example.groviq.backEnd.localFileProcessor

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.searchEngine.ArtistDto

data class metadataForLocalSongs(
    val title: String,
    val album: String,
    val artistDto: List<ArtistDto>,
    val year: String,
    val num: String?
)

@OptIn(UnstableApi::class)
fun extractMetadataFromFile(uri: Uri): metadataForLocalSongs {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(MyApplication.globalContext, uri)

    val title  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: DocumentFile.fromSingleUri(MyApplication.globalContext, uri)?.name
    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
    val album  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
    val year   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "Unknown Year"
    val num_   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: "0"

    retriever.release()

    val artistDto = ArtistDto(title = artist, imageUrl = "", albums = emptyList())
    return metadataForLocalSongs(
        title = title ?: "Unknown",
        album = album,
        artistDto = listOf(artistDto),
        year = year,
        num = num_
    )
}


