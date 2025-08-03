package com.example.groviq.backEnd.searchEngine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.audioEnterPoint
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.getPythonModule
import com.example.groviq.globalContext
import com.example.groviq.hasInternetConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

class SearchViewModel : ViewModel() {

    //states of UI
    val _uiState = MutableStateFlow(
        searchState()
    )
    val uiState: StateFlow < searchState > = _uiState.asStateFlow()


    //results getter
    private var searchJob: Job? = null
    fun getResultsOfSearchByString(context: Context, request: String) {
        val appContext = context.applicationContext

        //cancel previous task
        searchJob?.cancel()

        //creating a new job
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            if (!hasInternetConnection(globalContext!!)) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = mutableListOf(),
                        searchInProcess = false,
                        publicErrors = publucErrors.NO_INTERNET
                    )
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    searchResults = mutableListOf(),
                    searchInProcess = true
                )
            }

            try {
                val jsonString = getPythonModule(appContext)
                    .callAttr("searchOnServer", request)
                    .toString()

                val jsonObject = JSONObject(jsonString)
                if (jsonObject.has("error")) return@launch

                val tracks = parseJsonArray(jsonObject.getJSONArray("tracks"), searchType.SONG)
                val albums = parseJsonArray(jsonObject.getJSONArray("albums"), searchType.ALBUM)
                val artists = parseJsonArray(jsonObject.getJSONArray("artists"), searchType.ARTIST)

                val allResults = (tracks + albums + artists).toMutableList()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = allResults,
                        searchInProcess = false,
                        publicErrors = if (allResults.isEmpty()) publucErrors.NO_RESULTS else publucErrors.CLEAN
                    )
                }

            } catch (e: Exception) {
                Log.e("SearchError", "SearchingEngineError", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        searchInProcess = false,
                        publicErrors = publucErrors.UNKNOWN_ERROR
                    )
                }
            }
        }
    }
    private fun parseJsonArray(jsonArray: JSONArray, type : searchType): List<searchInfo> {
        return (0 until jsonArray.length()).map { i ->
            val item = jsonArray.getJSONObject(i)
            searchInfo(
                type = type,
                link_id     = item.optString("id"),
                title       = item.optString("name"),
                author      = if (type == searchType.ARTIST) "" else item.optString("artist"),
                image_url   = item.optString("image_url")
            )
        }
    }

    //album getter with one job active
    private var currentAlbumJob: Job? = null
    fun getAlbum(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {
        if (!hasInternetConnection(context)) {
            _uiState.update { it.copy(gettersInProcess = false) }
            return
        }

        //cancel job, if we have previous task active
        currentAlbumJob?.cancel()

        _uiState.update { it.copy(gettersInProcess = true) }

        currentAlbumJob = viewModelScope.launch {
            try {

                //request to server
                val albumMetaJson = withContext(Dispatchers.IO) {
                    getPythonModule(context)
                        .callAttr("getAlbum", request)
                        .toString()
                }

                //parsing
                val albumDto = parseAlbumJson(albumMetaJson)

                //mapping the results from parsed values
                val tracks = albumDto.tracks.map { t ->
                    songData(
                        link = t.url,
                        title = t.title,
                        artists = t.artists,
                        stream = "",
                        duration = t.duration_ms,
                        number = t.track_num,
                        progressStatus = songProgressStatus(),
                        playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
                        art = null
                    )
                }

                //update UI value
                withContext(Dispatchers.Main) {
                    mainViewModel.setAlbumTracks(request, tracks)
                }

            } catch (e: CancellationException) {
            } catch (e: Exception) {
                Log.e("getAlbum", "getter error", e)
            } finally {
                _uiState.update { it.copy(gettersInProcess = false) }
            }
        }
    }
    fun parseAlbumJson(jsonStr: String): AlbumResponse {

        val json = JSONObject(jsonStr)

        val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
        val tracks = mutableListOf<TrackDto>()

        for (i in 0 until tracksJson.length()) {
            val trackJson = tracksJson.getJSONObject(i)

            val artistsJson = trackJson.optJSONArray("artists") ?: JSONArray()
            val artists = mutableListOf<ArtistDto>()
            for (j in 0 until artistsJson.length()) {
                val artistJson = artistsJson.getJSONObject(j)
                artists.add(
                    ArtistDto(
                        name = artistJson.optString("name", ""),
                        url = artistJson.optString("url", "")
                    )
                )
            }

            tracks.add(
                TrackDto(
                    id = trackJson.optString("id", ""),
                    title = trackJson.optString("title", ""),
                    track_num = trackJson.optInt("track_num", 0),
                    url = trackJson.optString("url", ""),
                    duration_ms = trackJson.optLong("duration_ms", 0),
                    artists = artists
                )
            )
        }

        return AlbumResponse(
            album = json.optString("album", ""),
            artist = json.optString("artist", ""),
            artist_url = json.optString("artist_url", ""),
            year = json.optString("year", ""),
            image_url = json.optString("image_url", ""),
            tracks = tracks
        )
    }
}




