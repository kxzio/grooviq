package com.example.groviq.backEnd.searchEngine

import com.example.groviq.frontEnd.Screen
import kotlinx.serialization.Serializable

enum class searchType {
    NONE,
    SONG,
    ARTIST,
    ALBUM
}

data class searchInfo(
    var type      : searchType = searchType.NONE,
    var title     : String = "",
    var author    : String = "",
    var link_id      : String = "",
    var image_url : String = ""
)

data class ArtistDto(val name: String = "", val url: String = "")
data class TrackDto(
    val id: String,
    val title: String,
    val track_num: Int,
    val url: String,
    val duration_ms: Long,
    val artists: List<ArtistDto>
)
data class AlbumResponse(
    val album: String,
    val artist: String,
    val artist_url: String,
    val year: String,
    val image_url: String,
    val tracks: List<TrackDto>
)

enum class publucErrors {
    NO_INTERNET,
    NO_RESULTS,
    CLEAN,
    UNKNOWN_ERROR
}
data class searchState(

    //search structures
    var searchResults: MutableList < searchInfo > = mutableListOf(),

    //indicators
    var searchInProcess  : Boolean = false,
    var gettersInProcess : Boolean = false,
    var publicErrors    : publucErrors = publucErrors.CLEAN

)