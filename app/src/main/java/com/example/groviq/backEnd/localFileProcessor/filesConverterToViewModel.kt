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
    val num: String?,
    val isVideo : Boolean?
)

@OptIn(UnstableApi::class)
fun extractMetadataFromFile(uri: Uri): metadataForLocalSongs {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(MyApplication.globalContext, uri)

    val nameOfFile = DocumentFile.fromSingleUri(MyApplication.globalContext, uri)?.name

    val title  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: nameOfFile
    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
    val album  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
    val year   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "Unknown Year"
    val num_   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: "0"

    val format = nameOfFile?.substringAfterLast('.', "")

    val isVideo = format in listOf("mp4", "mkv", "avi", "mov", "webm")

    retriever.release()

    val artistDto = ArtistDto(title = artist, imageUrl = "", albums = emptyList())
    return metadataForLocalSongs(
        title = title ?: "Unknown",
        album = album,
        artistDto = listOf(artistDto),
        year = year,
        num = num_,
        isVideo = isVideo
    )
}


