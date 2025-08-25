package com.example.groviq.backEnd.searchEngine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.audioEnterPoint
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.dataStructures.songProgressStatus
import com.example.groviq.backEnd.dataStructures.streamInfo
import com.example.groviq.backEnd.playEngine.addToCurrentQueue
import com.example.groviq.backEnd.playEngine.queueElement
import com.example.groviq.backEnd.saveSystem.DataRepository
import com.example.groviq.getPythonModule
import com.example.groviq.hasInternetConnection
import com.example.groviq.loadBitmapFromUrl
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
import kotlin.random.Random


class SearchViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel() as T
    }
}

var currentRelatedTracksJob: Job? = null

//album getter with one job active
var currentAlbumJob: Job? = null

//artist getter with one job active
var currentArtistJob: Job? = null

var currentTrackJob: Job? = null


//recommend list, that we do before song ends
var preparedRecommendList : MutableList < songData > = mutableListOf()
var preparationInProgress : Boolean = false

//we store the song, that we already get the recomndation list, to use his again, to make new recommendations based on this song.
//reset on playerPressed play
var lastRecommendListProcessed : MutableList < String > = mutableListOf()

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
        searchJob?.cancel()

        searchJob = CoroutineScope(Dispatchers.IO).launch {
            if (!hasInternetConnection(
                    MyApplication.globalContext!!)) {
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

                val results = parseUnifiedJsonArray(jsonObject.getJSONArray("results"))

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = results.toMutableList(),
                        searchInProcess = false,
                        publicErrors = if (results.isEmpty()) publucErrors.NO_RESULTS else publucErrors.CLEAN
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

    private fun parseUnifiedJsonArray(jsonArray: JSONArray): List<searchInfo> {
        return (0 until jsonArray.length()).map { i ->
            val item = jsonArray.getJSONObject(i)
            val type = when (item.optString("type")) {
                "track" -> searchType.SONG
                "album" -> searchType.ALBUM
                "artist" -> searchType.ARTIST
                else -> searchType.SONG
            }
            searchInfo(
                type = type,
                link_id   = item.optString("id"),
                title     = item.optString("name"),
                author    = if (type == searchType.ARTIST) "" else item.optString("artist"),
                image_url = item.optString("image_url"),
                album_url = if (type == searchType.SONG) item.optString("album_url") else "",
            )
        }
    }


    fun getAlbum(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {
        if (!hasInternetConnection(context)) {
            _uiState.update { it.copy(gettersInProcess = false, publicErrors = publucErrors.NO_INTERNET) }
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

                val albumBitmap = loadBitmapFromUrl(albumDto.image_url)

                //mapping the results from parsed values
                val tracks = albumDto.tracks.map { t ->
                    songData(
                        link = t.url,
                        title = t.title,
                        artists = t.artists,
                        stream = streamInfo(),
                        duration = t.duration_ms,
                        number = t.track_num,
                        progressStatus = songProgressStatus(),
                        playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
                        art = albumBitmap,
                        album_original_link = request
                    )
                }

                //update UI value
                withContext(Dispatchers.Main) {
                    mainViewModel.setAlbumTracks(
                        request,
                        tracks,
                        albumDto.album,
                        albumDto.artists,
                        albumDto.year
                    )
                }

            } catch (e: CancellationException) {

            } catch (e: Exception) {

                Log.e("getAlbum", "getter error", e)
            } finally {
                _uiState.update { it.copy(gettersInProcess = false, publicErrors = publucErrors.CLEAN) }
            }
        }
    }

    fun parseAlbumJson(jsonStr: String): AlbumResponse {
        val json = JSONObject(jsonStr)

        //track parse
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
                        title = artistJson.optString("name", ""),
                        url = artistJson.optString("url", ""),
                        imageUrl = "",
                        albums = emptyList()
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

        //album parse
        val albumArtistsJson = json.optJSONArray("artist") ?: JSONArray()
        val albumArtists = mutableListOf<ArtistDto>()
        for (i in 0 until albumArtistsJson.length()) {
            val artistJson = albumArtistsJson.getJSONObject(i)
            albumArtists.add(
                ArtistDto(
                    title = artistJson.optString("name", ""),
                    url = artistJson.optString("url", ""),
                    imageUrl = "",
                    albums = emptyList()
                )
            )
        }

        return AlbumResponse(
            album = json.optString("album", ""),
            artists = albumArtists,
            year = json.optString("year", ""),
            image_url = json.optString("image_url", ""),
            tracks = tracks,
            link = ""
        )
    }

    fun getArtist(
        context: Context,
        request: String, mainViewModel: PlayerViewModel
    ) {
        if (!hasInternetConnection(context)) {
            _uiState.update { it.copy(gettersInProcess = false, publicErrors = publucErrors.NO_INTERNET) }
            return
        }

        //cancel previous job
        currentArtistJob?.cancel()

        _uiState.update { it.copy(gettersInProcess = true, publicErrors = publucErrors.CLEAN) }

        currentArtistJob = viewModelScope.launch {
            try {
                val artistJson = withContext(Dispatchers.IO) {
                    getPythonModule(context)
                        .callAttr("getArtist", request)
                        .toString()
                }

                val artist = parseArtistJson(artistJson, request)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(currentArtist = artist) }
                    mainViewModel.setAlbumTracks(
                        request,
                        artist.topTracks.map { track -> async { trackDtoToSongData(track) } }.awaitAll(),
                        "Popular tracks",
                        audioSourceArtist = emptyList(),
                        audioSourceYear = "",
                    )

                }

            } catch (e: CancellationException) {
                //cancel
            } catch (e: Exception) {
                Log.e("getArtist", "Artist load error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(gettersInProcess = false) }
            }
        }
    }

    fun parseArtistJson(jsonStr: String, url: String): ArtistDto {
        val json = JSONObject(jsonStr)

        val artistName = json.optString("artist", "")
        val imageUrl = json.optString("image_url", "").takeIf { it.isNotBlank() }
        val monthlyListeners = json.optInt("monthly_listeners", 0)

        val albumsJson = json.optJSONArray("albums") ?: JSONArray()
        val albums = mutableListOf<AlbumResponse>()
        for (i in 0 until albumsJson.length()) {
            val a = albumsJson.getJSONObject(i)
            albums.add(
                AlbumResponse(
                    album = a.optString("name", ""),
                    artists = listOf(
                        ArtistDto(
                            title = artistName,
                            url = url,
                            imageUrl = "",
                            albums = emptyList(),
                            topTracks = emptyList()
                        )
                    ),
                    tracks = emptyList(),
                    image_url = a.optString("image_url", ""),
                    link = a.optString("url", null),
                    year = a.optString("year", null)
                )
            )
        }

        // related artists
        val relatedArtistsJson = json.optJSONArray("related_artists") ?: JSONArray()
        val relatedArtists = mutableListOf<miniArtistDto>()
        for (i in 0 until relatedArtistsJson.length()) {
            val ar = relatedArtistsJson.getJSONObject(i)
            relatedArtists.add(
                miniArtistDto(
                    title = ar.optString("name", ""),
                    imageUrl = ar.optString("image_url").takeIf { it.isNotBlank() },
                    url = ar.optString("url", "")
                )
            )
        }

        // top tracks
        val topTracksJson = json.optJSONArray("top_tracks") ?: JSONArray()
        val topTracks = mutableListOf<TrackDto>()
        for (i in 0 until topTracksJson.length()) {
            val t = topTracksJson.getJSONObject(i)

            val artistsJson = t.optJSONArray("artists") ?: JSONArray()
            val artists = mutableListOf<ArtistDto>()
            for (j in 0 until artistsJson.length()) {
                val ar = artistsJson.getJSONObject(j)
                artists.add(
                    ArtistDto(
                        title = ar.optString("name", ""),
                        url = ar.optString("url", ""),
                        imageUrl = null,
                        albums = emptyList(),
                        topTracks = emptyList()
                    )
                )
            }

            topTracks.add(
                TrackDto(
                    id = t.optString("id", ""),
                    title = t.optString("name", ""),
                    imageUrl = t.optString("album_image_url", ""),
                    track_num = 0,
                    url = t.optString("url", ""),
                    duration_ms = t.optLong("duration_ms", 0L),
                    artists = artists,
                    albumUrl = t.optString("album_url", ""),
                )
            )
        }

        return ArtistDto(
            title = artistName,
            imageUrl = imageUrl,
            albums = albums,
            url = url,
            topTracks = topTracks,
            relatedArtists = relatedArtists
        )
    }

    fun parseTrackJson(jsonStr: String): TrackDto {
        val json = JSONObject(jsonStr)

        // Артисты
        val artistsJson = json.optJSONArray("artists") ?: JSONArray()
        val artists = mutableListOf<ArtistDto>()
        for (i in 0 until artistsJson.length()) {
            val artistJson = artistsJson.getJSONObject(i)
            artists.add(
                ArtistDto(
                    title = artistJson.optString("name", ""),
                    url = artistJson.optString("url", ""),
                    imageUrl = "",
                    albums = emptyList()
                )
            )
        }

        return TrackDto(
            title = json.optString("title", ""),
            artists = artists,
            duration_ms = json.optLong("duration_ms", 0),
            imageUrl = json.optString("image_url", ""),
            url = json.optString("url", ""),
            id = "",
            track_num = 0,
            albumUrl = json.optString("album_url", ""),

            )
    }

    fun getTrack(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {
        if (!hasInternetConnection(context)) {
            _uiState.update { it.copy(gettersInProcess = false, publicErrors = publucErrors.NO_INTERNET) }
            return
        }

        currentTrackJob?.cancel()

        _uiState.update { it.copy(gettersInProcess = true) }

        currentTrackJob = viewModelScope.launch {
            try {
                val trackMetaJson = withContext(Dispatchers.IO) {
                    getPythonModule(context)
                        .callAttr("getTrack", request)
                        .toString()
                }

                val trackDto = parseTrackJson(trackMetaJson)

                val trackBitmap = loadBitmapFromUrl(trackDto.imageUrl!!)

                val trackData = songData(
                    link = trackDto.url,
                    title = trackDto.title,
                    artists = trackDto.artists,
                    stream = streamInfo(),
                    duration = trackDto.duration_ms,
                    number = 1,
                    progressStatus = songProgressStatus(),
                    playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
                    art = trackBitmap,
                    album_original_link = trackDto.albumUrl
                )

                withContext(Dispatchers.Main) {
                    mainViewModel.setTrack(request, trackData)
                }

            } catch (e: CancellationException) {

            } catch (e: Exception) {
                Log.e("getTrack", "getter error", e)
            } finally {
                _uiState.update { it.copy(gettersInProcess = false, publicErrors = publucErrors.CLEAN) }
            }
        }
    }

    suspend fun trackDtoToSongData(track: TrackDto): songData {
        val albumBitmap = loadBitmapFromUrl(track.imageUrl ?: "")

        return songData(
            link = track.url,
            title = track.title,
            artists = track.artists,
            stream = streamInfo(),
            duration = track.duration_ms,
            number = track.track_num,
            progressStatus = songProgressStatus(),
            playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
            art = albumBitmap,
            album_original_link = track.albumUrl
        )
    }

    suspend fun addRelatedTracksToCurrentQueue(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ): String {

        if (!hasInternetConnection(context)) return ""

        return withContext(Dispatchers.IO)
        {
            try {

                val random = Random.nextInt(0, 1488)

                if (preparedRecommendList.isNullOrEmpty() && preparationInProgress == false)
                {
                    val trackMetaJson = try {

                        //we should get another same list for this song again
                        if (lastRecommendListProcessed.isNullOrEmpty().not())
                        {
                            val gson = Gson()
                            val json = gson.toJson(lastRecommendListProcessed)
                            getPythonModule(context)
                                .callAttr("replaceSongs", json)
                                .toString()
                        }
                        else
                        {
                            getPythonModule(context)
                                .callAttr("getRelatedTracks", request)
                                .toString()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        null
                    }

                    val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()
                    val tracks = trackDtos.map { trackDtoToSongData(it) }

                    withContext(Dispatchers.Main) {
                        mainViewModel.setAlbumTracks(
                            request + "source-related-tracks" + random,
                            tracks,
                            audioSourceName = "Похожие треки",
                            audioSourceArtist = emptyList(),
                            audioSourceYear = ""
                        )

                        tracks.forEach { track ->
                            addToCurrentQueue(mainViewModel, track.link, request + "source-related-tracks" + random)
                        }

                        mainViewModel.setSongsLoadingStatus(false)
                        lastRecommendListProcessed = tracks.map { it.link }.toMutableList()
                    }

                    request + "source-related-tracks" + random
                }
                else if (preparedRecommendList.isNullOrEmpty() && preparationInProgress == true)
                {
                    while (preparedRecommendList.isNullOrEmpty()) {
                        delay(100)
                    }

                    val tracks = preparedRecommendList

                    withContext(Dispatchers.Main) {
                        mainViewModel.setAlbumTracks(
                            request + "source-related-tracks" + random,
                            tracks,
                            audioSourceName = "Похожие треки",
                            audioSourceArtist = emptyList(),
                            audioSourceYear = ""
                        )

                        tracks.forEach { track ->
                            addToCurrentQueue(mainViewModel, track.link, request + "source-related-tracks" + random)
                        }

                        mainViewModel.setSongsLoadingStatus(false)
                        lastRecommendListProcessed = tracks.map { it.link }.toMutableList()
                    }

                    request + "source-related-tracks" + random
                }
                else {

                    val tracks = preparedRecommendList

                    withContext(Dispatchers.Main) {
                        mainViewModel.setAlbumTracks(
                            request + "source-related-tracks" + random,
                            tracks,
                            audioSourceName = "Похожие треки",
                            audioSourceArtist = emptyList(),
                            audioSourceYear = ""
                        )

                        tracks.forEach { track ->
                            addToCurrentQueue(mainViewModel, track.link, request + "source-related-tracks" + random)
                        }

                        mainViewModel.setSongsLoadingStatus(false)
                        lastRecommendListProcessed = tracks.map { it.link }.toMutableList()
                    }

                    request + "source-related-tracks" + random
                }


            } finally {
                withContext(
                    NonCancellable + Dispatchers.Main) {
                    mainViewModel.setSongsLoadingStatus(false)
                }
            }
        }
    }

    suspend fun addRelatedTracksToAudioSource(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ): String {
        if (!hasInternetConnection(context)) return ""

        _uiState.update { it.copy(gettersInProcess = true) }

        val sourceKey = "${request}_source-related-tracks_radio"

        return withContext(Dispatchers.IO) {
            try {
                val trackMetaJson = try {
                    if (!lastRecommendListProcessed.isNullOrEmpty()) {
                        val gson = Gson()
                        val json = gson.toJson(lastRecommendListProcessed)
                        getPythonModule(context).callAttr("replaceSongs", json).toString()
                    } else {
                        getPythonModule(context).callAttr("getRelatedTracks", request).toString()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    null
                }

                val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()
                val tracks = trackDtos.map { trackDtoToSongData(it) }

                withContext(Dispatchers.Main) {
                    mainViewModel.setAlbumTracks(
                        sourceKey,
                        tracks,
                        audioSourceName = "Похожие треки",
                        audioSourceArtist = emptyList(),
                        audioSourceYear = ""
                    )
                }

                sourceKey
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    _uiState.update { it.copy(gettersInProcess = false) }
                }
            }
        }
    }

    suspend fun prepareRelatedTracks(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ){

        preparationInProgress = true

        if (!hasInternetConnection(context)) return

        withContext(Dispatchers.IO) {
            try {

                if (preparedRecommendList.isNullOrEmpty())
                {
                    val trackMetaJson = try {

                        //we should get another same list for this song again
                        if (lastRecommendListProcessed.isNullOrEmpty().not())
                        {
                            val gson = Gson()
                            val json = gson.toJson(lastRecommendListProcessed)
                            getPythonModule(context)
                                .callAttr("replaceSongs", json)
                                .toString()
                        }
                        else
                        {
                            getPythonModule(context)
                                .callAttr("getRelatedTracks", request)
                                .toString()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        null
                    }

                    val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()
                    val tracks = trackDtos.map { trackDtoToSongData(it) }

                    withContext(Dispatchers.Main) {
                        preparedRecommendList = tracks.toMutableList()
                        preparationInProgress = false
                    }

                }

            } finally {
                withContext(
                    NonCancellable + Dispatchers.Main) {
                    preparationInProgress = false
                }
            }
        }
    }

    fun parseRelatedJson(jsonStr: String): List<TrackDto> {
        val json = JSONObject(jsonStr)
        val resultsJson = json.optJSONObject("results") ?: JSONObject()
        val allTracks = mutableListOf<TrackDto>()

        val keys = resultsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val trackArray = resultsJson.optJSONArray(key) ?: JSONArray()

            for (i in 0 until trackArray.length()) {
                val trackJson = trackArray.getJSONObject(i)

                val artistsJson = trackJson.optJSONArray("artists") ?: JSONArray()
                val artists = mutableListOf<ArtistDto>()
                for (j in 0 until artistsJson.length()) {
                    val artistJson = artistsJson.getJSONObject(j)
                    artists.add(
                        ArtistDto(
                            title = artistJson.optString("name", ""),
                            url = artistJson.optString("url", ""),
                            imageUrl = "",
                            albums = emptyList()
                        )
                    )
                }

                allTracks.add(
                    TrackDto(
                        id = trackJson.optString("id", ""),
                        title = trackJson.optString("title", ""),
                        track_num = 0,
                        url = trackJson.optString("url", ""),
                        duration_ms = trackJson.optLong("duration_ms", 0),
                        artists = artists,
                        imageUrl = trackJson.optString("image_url", ""),
                        albumUrl = trackJson.optString("album_url", "")
                    )
                )
            }
        }

        return allTracks
    }
}



