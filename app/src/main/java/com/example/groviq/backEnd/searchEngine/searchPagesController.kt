package com.example.groviq.backEnd.searchEngine

import androidx.navigation.NavController
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.canNavigate
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
    var link_id   : String = "",
    var image_url : String = "",
    var album_url : String = ""
)

data class miniArtistDto(
    val title: String,
    val imageUrl: String?,
    val url: String = "",
)

data class ArtistDto(
    val title: String,
    val imageUrl: String?,
    val albums: List<AlbumResponse>,
    val url: String = "",
    val topTracks: List<TrackDto> = emptyList(),
    val relatedArtists : List<miniArtistDto> = emptyList()
)

data class TrackDto(
    val id: String,
    val title: String,
    val track_num: Int,
    val url: String,
    val duration_ms: Long,
    val artists: List<ArtistDto>,
    val imageUrl: String? = null,
    val albumUrl: String = ""
)
data class AlbumResponse(
    val album: String,
    val year: String,
    val image_url: String,
    val artists: List<ArtistDto>,
    val tracks: List<TrackDto>,
    val link : String //for artist albums
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

    //curent opened artist in browser
    var currentArtist: ArtistDto = ArtistDto("", "", emptyList(), "", emptyList()),

    //indicators
    var searchInProcess  : Boolean = false,
    var gettersInProcess : Boolean = false,

    var publicErrors     :  MutableMap<String, publucErrors> = mutableMapOf(),

)

fun MutableMap<String, publucErrors>.removeIfRouteEmpty(navController: NavController) {

    val keysToRemove = this.keys.filter { route ->

        if (route == "search") false

        canNavigate(navController, route).not()
    }

    keysToRemove.forEach { key -> this.remove(key) }
}