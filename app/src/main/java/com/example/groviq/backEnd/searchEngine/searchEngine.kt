package com.example.groviq.backEnd.searchEngine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
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
import com.example.groviq.frontEnd.Screen
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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

// daily playlist getter with one job active
var currentDailyListJob : Job? = null
var currentMoodListJob  : Job? = null
var currentChartJob : Job? = null

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

    @OptIn(UnstableApi::class)
    fun getResultsOfSearchByString(context: Context, request: String) {
        val appContext = context.applicationContext
        searchJob?.cancel()

        searchJob = CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            val maxRetries = 5

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    searchResults = mutableListOf(),
                    searchInProcess = true,
                    publicErrors = _uiState.value.publicErrors.toMutableMap().apply {
                        this["search"] = publucErrors.CLEAN
                    }
                )
            }

            while (isActive && attempt < maxRetries) {
                if (!hasInternetConnection(appContext)) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            publicErrors = _uiState.value.publicErrors.toMutableMap().apply {
                                this["search"] = publucErrors.NO_INTERNET
                            }
                        )
                    }

                    delay(2000L)
                    attempt++
                    continue
                }

                try {
                    val jsonString = getPythonModule(appContext)
                        .callAttr("searchOnServer", request)
                        .toString()

                    val jsonObject = JSONObject(jsonString)
                    if (jsonObject.has("error")) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                searchInProcess = false,
                                publicErrors = _uiState.value.publicErrors.toMutableMap().apply {
                                    this["search"] = publucErrors.UNKNOWN_ERROR
                                }
                            )
                        }
                        return@launch
                    }

                    val results = parseUnifiedJsonArray(jsonObject.getJSONArray("results"))

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            searchResults = results.toMutableList(),
                            searchInProcess = false,
                            publicErrors = if (results.isEmpty())

                                _uiState.value.publicErrors.toMutableMap().apply {
                                    this["search"] = publucErrors.NO_RESULTS
                                }

                                else

                                uiState.value.publicErrors.toMutableMap().apply {
                                    this["search"] = publucErrors.CLEAN
                                }

                        )
                    }
                    return@launch

                } catch (e: Exception) {
                    Log.e("SearchError", "SearchingEngineError", e)
                    attempt++
                    delay(2000L * attempt)
                }
            }

            // Если все попытки исчерпаны
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    searchInProcess = false,
                    publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                        this["search"] = publucErrors.UNKNOWN_ERROR
                    }
                )
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

    @OptIn(UnstableApi::class)
    fun getAlbum(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {

        val navigationSaver = Screen.Searching.route + "/album/" + request

        currentAlbumJob?.cancel()

        currentAlbumJob = viewModelScope.launch {
            val maxRetries = 3
            val timeoutPerTry = 10000L
            var success = false

            _uiState.update { it.copy(
                gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                    this[navigationSaver] = true
                },
                publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                this[navigationSaver] = publucErrors.CLEAN
            }) }

            repeat(maxRetries) { attempt ->
                if (!hasInternetConnection(context)) {
                    delay(2000L * (attempt + 1))
                    return@repeat
                }

                try {
                    val albumMetaJson = withTimeoutOrNull(timeoutPerTry) {
                        withContext(Dispatchers.IO) {
                            getPythonModule(context)
                                .callAttr("getAlbumNoVideo", request)
                                .toString()
                        }
                    } ?: run {
                        return@repeat
                    }

                    val albumDto = parseAlbumJson(albumMetaJson)

                    if (albumDto.artists.isNullOrEmpty() || albumDto.image_url.isNullOrEmpty()) {
                        return@repeat
                    }

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
                            art_local_link = null,
                            art_link = albumDto.image_url,
                            album_original_link = request
                        )
                    }

                    withContext(Dispatchers.Main) {
                        mainViewModel.setAlbumTracks(
                            request,
                            tracks,
                            albumDto.album,
                            albumDto.artists,
                            albumDto.year
                        )
                        _uiState.update { it.copy(
                            gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                                this[navigationSaver] = false
                            },
                            publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                            this[navigationSaver] = publucErrors.CLEAN
                        }) }
                    }

                    success = true
                    return@launch

                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    Log.e("getAlbum", "getter error (try $attempt)", e)
                    delay(1500L * (attempt + 1))
                }
            }

            if (!success) {
                _uiState.update {
                    it.copy(
                        gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                            this[navigationSaver] = false
                        },
                        publicErrors =  if (!hasInternetConnection(context)) uiState.value.publicErrors.toMutableMap().apply {
                            this[navigationSaver] = publucErrors.NO_INTERNET
                        } else uiState.value.publicErrors.toMutableMap().apply {
                            this[navigationSaver] = publucErrors.NO_RESULTS
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(
                    gettersInProcess = uiState.value.gettersInProcess.toMutableMap()
                        .apply {
                            this[navigationSaver] =
                                false
                        },
                ) }
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

    @OptIn(UnstableApi::class)
    fun getArtist(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {

        val navigationSaver = Screen.Searching.route + "/artist/" + request

        currentArtistJob?.cancel()

        currentArtistJob = viewModelScope.launch {
            val maxRetries = 3
            val timeoutPerTry = 10000L
            var success = false

            _uiState.update { it.copy(
                gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                    this[navigationSaver] = true
                },
                publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                this[ navigationSaver ] = publucErrors.CLEAN
            }) }

            repeat(maxRetries) { attempt ->
                if (!hasInternetConnection(context)) {
                    delay(2000L * (attempt + 1))
                    return@repeat
                }

                try {
                    val artistJson = withTimeoutOrNull(timeoutPerTry) {
                        withContext(Dispatchers.IO) {
                            getPythonModule(context)
                                .callAttr("getArtist", request)
                                .toString()
                        }
                    } ?: run {
                        return@repeat
                    }

                    val artist = parseArtistJson(artistJson, request)

                    if (artist.url.isNullOrEmpty() ||
                        artist.title.isNullOrEmpty() ||
                        artist.imageUrl.isNullOrEmpty()
                    ) {
                        return@repeat
                    }

                    val topTracks = artist.topTracks.map { track ->
                        async { trackDtoToSongData(track) }
                    }.awaitAll()

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                currentArtist = artist,
                                publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                                    this[ navigationSaver ] = publucErrors.CLEAN
                                },
                                gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                                    this[navigationSaver] = false
                                },
                            )
                        }
                        mainViewModel.setAlbumTracks(
                            request,
                            topTracks,
                            "Popular tracks",
                            audioSourceArtist = emptyList(),
                            audioSourceYear = "",
                        )
                    }

                    success = true
                    return@launch

                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    Log.e("getArtist", "Artist load error (try $attempt)", e)
                    delay(1500L * (attempt + 1))
                }
            }

            if (!success) {
                _uiState.update {
                    it.copy(
                        gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                            this[navigationSaver] = false
                        },
                        publicErrors = if (!hasInternetConnection(context)) uiState.value.publicErrors.toMutableMap().apply {
                            this[ navigationSaver] = publucErrors.NO_INTERNET
                        } else uiState.value.publicErrors.toMutableMap().apply {
                            this[ navigationSaver] = publucErrors.NO_RESULTS
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(
                    gettersInProcess = uiState.value.gettersInProcess.toMutableMap()
                        .apply {
                            this[navigationSaver] =
                                false
                        },
                ) }
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

    @OptIn(UnstableApi::class)
    fun getTrack(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ) {
        currentTrackJob?.cancel()

        currentTrackJob = viewModelScope.launch {
            val maxRetries = 3
            val timeoutPerTry = 8000L
            var success = false

            repeat(maxRetries) { attempt ->
                if (!hasInternetConnection(context)) {
                    delay(2000L * (attempt + 1))
                    return@repeat
                }

                try {
                    val trackMetaJson = withTimeoutOrNull(timeoutPerTry) {
                        withContext(Dispatchers.IO) {
                            getPythonModule(context)
                                .callAttr("getTrack", request)
                                .toString()
                        }
                    } ?: run {
                        return@repeat
                    }

                    val trackDto = parseTrackJson(trackMetaJson)

                    if (trackDto.url.isNullOrEmpty() ||
                        trackDto.title.isNullOrEmpty() ||
                        trackDto.artists.isEmpty() ||
                        trackDto.imageUrl.isNullOrEmpty()
                    ) {
                        return@repeat
                    }

                    val trackData = songData(
                        link = trackDto.url,
                        title = trackDto.title,
                        artists = trackDto.artists,
                        stream = streamInfo(),
                        duration = trackDto.duration_ms,
                        number = 1,
                        progressStatus = songProgressStatus(),
                        playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
                        art_local_link = null,
                        art_link = trackDto.imageUrl,
                        album_original_link = trackDto.albumUrl
                    )

                    withContext(Dispatchers.Main) {
                        mainViewModel.setTrack(request, trackData)
                    }

                    success = true
                    return@launch

                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    Log.e("getTrack", "getter error (try $attempt)", e)
                    delay(1500L * (attempt + 1))
                }
            }

            if (!success) {

            } else {

            }
        }
    }

    fun trackDtoToSongData(track: TrackDto): songData {
        return songData(
            link = track.url,
            title = track.title,
            artists = track.artists,
            stream = streamInfo(),
            duration = track.duration_ms,
            number = track.track_num,
            progressStatus = songProgressStatus(),
            playingEnterPoint = audioEnterPoint.NOT_PLAYABLE,
            art_link = track.imageUrl!!,
            album_original_link = track.albumUrl
        )
    }

    @OptIn(UnstableApi::class)
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


                            getPythonModule(context)
                                .callAttr("getRelatedTracks", request)
                                .toString()


                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        null
                    }

                    val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()
                    var tracks = trackDtos.map { trackDtoToSongData(it) }

                    tracks = tracks.filter { it.link != request }

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

                    var tracks = preparedRecommendList

                    tracks = tracks.filter { it.link != request }.toMutableList()

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

                    var tracks = preparedRecommendList

                    tracks = tracks.filter { it.link != request }.toMutableList()

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

    @OptIn(UnstableApi::class)
    suspend fun addRelatedTracksToAudioSource(
        context: Context,
        request: String,
        mainViewModel: PlayerViewModel
    ): String {
        if (!hasInternetConnection(context)) return ""

        val sourceKey = "${request}_source-related-tracks_radio"

        val navigationSaver = Screen.Searching.route + "/radio/" + request

        _uiState.update { it.copy(
            gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                this[navigationSaver] = true
            },
            publicErrors = uiState.value.publicErrors.toMutableMap().apply {
            this[navigationSaver] = publucErrors.CLEAN
        }) }

        return withContext(Dispatchers.IO) {
            try {
                val trackMetaJson = try {
                    getPythonModule(context).callAttr("getRelatedTracks", request).toString()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    null
                }

                val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()

                if (trackDtos.isNullOrEmpty())
                {
                    _uiState.update { it.copy(
                        gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                        this[navigationSaver] = false
                    },
                        publicErrors = uiState.value.publicErrors.toMutableMap().apply {
                        this[navigationSaver] = publucErrors.NO_RESULTS
                    }) }

                    return@withContext ""
                }

                var tracks = trackDtos.map { trackDtoToSongData(it) }

                tracks = tracks.filter { it.link != request }

                withContext(Dispatchers.Main) {
                    mainViewModel.setAlbumTracks(
                        sourceKey,
                        tracks,
                        audioSourceName = "Radio " + mainViewModel.uiState.value.allAudioData[request]?.title,
                        audioSourceArtist = emptyList(),
                        audioSourceYear = ""
                    )
                    _uiState.update { it.copy(
                        gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                            this[navigationSaver] = false
                        },
                        publicErrors =  uiState.value.publicErrors.toMutableMap().apply {
                        this[navigationSaver] = publucErrors.CLEAN
                    }) }
                }

                sourceKey
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    _uiState.update { it.copy(
                        gettersInProcess = uiState.value.gettersInProcess.toMutableMap().apply {
                            this[navigationSaver] = false
                        },
                    ) }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
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


                            getPythonModule(context)
                                .callAttr("getRelatedTracks", request)
                                .toString()


                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        null
                    }

                    val trackDtos = trackMetaJson?.let { parseRelatedJson(it) } ?: emptyList()
                    val tracks = trackDtos.map { trackDtoToSongData(it) }

                    withContext(Dispatchers.Main) {
                        preparedRecommendList = tracks.filter { it.link != request }.toMutableList()
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

    @OptIn(UnstableApi::class)
    fun fetchUniqueRecommendations(
        seedSongs: List<String>,
        maxResults: Int,
        likedSongs: Set<String>,
        allLocalSongs: Collection<songData>,
        perBatchCount: Int = 3,
        getOnlyThisArtist: Boolean = false
    ): List<songData> {
        val collected = mutableListOf<songData>()
        val seen = mutableSetOf<String>()
        var queue = seedSongs.toMutableList()

        var safetyCounter = 0
        val maxIterations = 50

        val targetArtistUrl: String? = if (getOnlyThisArtist) {
            allLocalSongs.firstOrNull { it.link == seedSongs.firstOrNull() }
                ?.artists
                ?.firstOrNull()
                ?.url
        } else null

        while (collected.size < maxResults && queue.isNotEmpty() && safetyCounter < maxIterations) {
            safetyCounter++

            val batch = queue.take(10)
            queue = queue.drop(10).toMutableList()

            val trackMetaJson = try {
                getPythonModule(MyApplication.globalContext!!)
                    .callAttr("getRelatedTracks", batch.toTypedArray(), perBatchCount)
                    .toString()
            } catch (e: Exception) {
                null
            }

            if (trackMetaJson == null) continue

            val trackDtos = parseRelatedJson(trackMetaJson)
            var foundNew = false

            for (dto in trackDtos) {
                val song = trackDtoToSongData(dto)

                if (getOnlyThisArtist && song.artists.firstOrNull()?.url != targetArtistUrl) {
                    continue
                }

                if (getOnlyThisArtist) {
                    if (song.link !in seen) {
                        collected.add(song)
                        seen.add(song.link)
                        foundNew = true
                    }
                } else {
                    if (song.link !in likedSongs && song.link !in seen) {
                        collected.add(song)
                        seen.add(song.link)
                        foundNew = true
                    } else if (song.link in likedSongs && song.link !in seen) {
                        queue.add(song.link)
                        seen.add(song.link)
                    }
                }

                if (collected.size >= maxResults) break
            }

            if (!foundNew && queue.isEmpty()) break
        }

        
        if (collected.size < maxResults) {
            val fallbackSongs = allLocalSongs
                .filter {
                    it.link !in seen &&
                            (!getOnlyThisArtist && it.link !in likedSongs || getOnlyThisArtist) &&
                            (!getOnlyThisArtist || it.artists.firstOrNull()?.url == targetArtistUrl)
                }
                .shuffled()
                .take(maxResults - collected.size)

            collected.addAll(fallbackSongs)

        }

        return collected
    }



    @OptIn(UnstableApi::class)
    fun createDailyPlaylist(mainViewModel: PlayerViewModel) {

        // if a job is already running, don't start a new one
        if (currentDailyListJob?.isActive == true) return

        val uiState = mainViewModel.uiState.value
        val biggestPlaylist = uiState.audioData.entries.filter {
            mainViewModel.isPlaylist(it.key) && uiState.audioData[it.key]?.autoGenerated?.not() ?: false
        }.maxByOrNull { it.value.songIds.size } ?: return

        val audioSource = biggestPlaylist.value

        // check if there are enough songs to create a playlist
        if (audioSource.songIds.size < 10) return // not enough songs, abort

        // collect initial song hashes
        val playlistNewSongsHashes = mutableListOf<String>().apply {
            // add 3 random songs
            repeat(3) {
                audioSource.songIds.randomOrNull()?.let { add(it) }
            }

            // add last 5 songs
            addAll(audioSource.songIds.takeLast(5))

            // get top 3 artists and pick 3 songs each (balanced)
            val songs = audioSource.songIds.mapNotNull { uiState.allAudioData[it] }
            val topArtists = songs.groupingBy { it.artists[0].url }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            val balancedTopSongs = topArtists.flatMap { artistUrl ->
                songs.filter { it.artists[0].url == artistUrl }.shuffled().take(3)
            }.shuffled()

            addAll(balancedTopSongs.map { it.link })

            // random diversity songs
            val diversitySongs = songs.groupBy { it.artists[0].url }
                .values
                .mapNotNull { it.randomOrNull() }
                .shuffled()
                .take(3)

            addAll(diversitySongs.map { it.link })

            shuffle()
        }

        if (uiState.audioData.get("DAILY_PLAYLIST_AUTOGENERATED") != null)
        {
            uiState.audioData.get("DAILY_PLAYLIST_AUTOGENERATED")?.isInGenerationProcess = true
        }


        // start coroutine to fetch related tracks
        currentDailyListJob = viewModelScope.launch {
            try {
                // take only first 20 tracks for request
                val limitedSongs = playlistNewSongsHashes.take(20)

                // perform heavy work on io dispatcher
                var allTracks = withContext(Dispatchers.IO) {
                    fetchUniqueRecommendations(
                        seedSongs = limitedSongs,
                        maxResults = 25,
                        likedSongs = uiState.allAudioData.keys,
                        allLocalSongs = uiState.allAudioData.values,
                        perBatchCount = 4
                    )
                }

                var finalLimited = allTracks.distinctBy { it.link }.take(25)

                finalLimited = finalLimited.shuffled()

                // check if we actually got enough tracks
                if (finalLimited.isEmpty()) {
                    uiState.audioData["DAILY_PLAYLIST_AUTOGENERATED"]?.isInGenerationProcess = false
                    return@launch
                }

                // update ui on main thread
                withContext(Dispatchers.Main) {
                    mainViewModel.setAlbumTracks(
                        "DAILY_PLAYLIST_AUTOGENERATED",
                        finalLimited,
                        audioSourceName = "Daily Mix",
                        audioSourceArtist = emptyList(),
                        audioSourceYear = "",
                        autoGeneratedB = true
                    )
                    uiState.audioData.get("DAILY_PLAYLIST_AUTOGENERATED")?.isInGenerationProcess = false
                    mainViewModel.saveAudioSourcesToRoom()
                    mainViewModel.saveSongsFromSourceToRoom("DAILY_PLAYLIST_AUTOGENERATED")
                }
            } catch (e: CancellationException) {
                // job was cancelled, ignore
            } catch (e: Exception) {
                // log other unexpected errors
                e.printStackTrace()
            } finally {
                // cleanup job reference
                currentDailyListJob = null
            }
        }
    }
    @OptIn(UnstableApi::class)
    fun createFourSpecialPlaylists(mainViewModel: PlayerViewModel) {
        if (currentMoodListJob?.isActive == true) return

        val uiState = mainViewModel.uiState.value
        val biggestPlaylist = uiState.audioData.entries.filter {
            mainViewModel.isPlaylist(it.key) && uiState.audioData[it.key]?.autoGenerated?.not() ?: false
        }.maxByOrNull { it.value.songIds.size } ?: return
        val audioSource = biggestPlaylist.value
        val allSongs = audioSource.songIds.mapNotNull { uiState.allAudioData[it] }
        if (allSongs.size < 10) return

        val topArtists = allSongs.groupingBy { it.artists[0] }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }

        val lastSongs = audioSource.songIds.takeLast(30).mapNotNull { uiState.allAudioData[it] }
        var newArtists = lastSongs.map { it.artists[0] }
            .distinct()
            .filter { it !in topArtists }
            .take(2)

        if (newArtists.isEmpty()) {
            newArtists = lastSongs.map { it.artists[0] }.distinct().take(2)
        }

        if (newArtists.any { it in topArtists }) {
            val randomArtist = uiState.allAudioData.values.shuffled().firstOrNull()?.artists?.firstOrNull()
            if (randomArtist != null) newArtists = (newArtists + randomArtist).distinct().take(2)
        }

        val playlistsMap = mutableMapOf<String, Pair<String, MutableList<songData>>>()
        val moods = uiState.audioData.filter { it.key.startsWith("MOOD_PLAYLIST_AUTOGENERATED") }
        moods.forEach { uiState.audioData[it.key]?.isInGenerationProcess = true }

        currentMoodListJob?.cancel()
        currentMoodListJob = viewModelScope.launch {
            try {
                val playlistRequests = mutableListOf<Triple<String, String, List<String>>>()
                topArtists.forEachIndexed { index, artist ->
                    playlistRequests.add(
                        Triple(
                            "mood_$index",
                            "My mood",
                            allSongs.filter { it.artists[0].url == artist.url }.map { it.link }
                        )
                    )
                    playlistRequests.add(
                        Triple(
                            "only_artist_$index",
                            artist.title,
                            allSongs.filter { it.artists[0].url == artist.url }.map { it.link }
                        )
                    )
                }
                newArtists.forEachIndexed { index, artist ->
                    playlistRequests.add(
                        Triple(
                            "like_$index",
                            "You may like",
                            lastSongs.filter { it.artists[0].url == artist.url }.map { it.link }
                        )
                    )
                }

                val deferredResults = playlistRequests.map { (uniqueKey, displayName, hashes) ->
                    async(Dispatchers.IO) {
                        val playlistTracks = fetchUniqueRecommendations(
                            seedSongs = hashes,
                            maxResults = 15,
                            likedSongs = uiState.allAudioData.keys,
                            allLocalSongs = allSongs.filter { it.link in hashes }.toMutableList(),
                            perBatchCount = if (uniqueKey.contains("only_artist_")) 35 else 5,
                            getOnlyThisArtist = uniqueKey.contains("only_artist_")
                        ).toMutableList()
                        uniqueKey to (displayName to playlistTracks)
                    }
                }

                val results = deferredResults.awaitAll()
                playlistsMap.putAll(results)

                withContext(Dispatchers.Main) {
                    playlistsMap.entries.forEachIndexed { index, (uniqueKey, pair) ->
                        val (displayName, tracks) = pair
                        if (tracks.size > 10) {
                            mainViewModel.setAlbumTracks(
                                "MOOD_PLAYLIST_AUTOGENERATED_${index + 1}",
                                tracks.distinctBy { it.link }.take(20).shuffled(),
                                audioSourceName = displayName,
                                audioSourceArtist = emptyList(),
                                audioSourceYear = "",
                                autoGeneratedB = true
                            )
                            uiState.audioData["MOOD_PLAYLIST_AUTOGENERATED_${index + 1}"]?.isInGenerationProcess = false
                            mainViewModel.saveSongsFromSourceToRoom("MOOD_PLAYLIST_AUTOGENERATED_${index + 1}")
                        }
                    }
                    mainViewModel.saveAudioSourcesToRoom()
                }
            } catch (e: CancellationException) {
                // ignore
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    val playlistsAutoGenerated = uiState.audioData.entries.filter { it.value.autoGenerated }
                    playlistsAutoGenerated.forEach { uiState.audioData[it.key]?.isInGenerationProcess = false }
                }
            }
        }
    }




}

object SearchViewModelHelpers {

    private val generationFlags = ConcurrentHashMap<String, AtomicBoolean>()

    @OptIn(
        androidx.media3.common.util.UnstableApi::class)
    fun requestGenerationIfNeeded(
        flagKey: String,
        mainViewModel: PlayerViewModel,
        createAction: (PlayerViewModel) -> Unit
    ) {
        val flag = generationFlags.computeIfAbsent(flagKey) { AtomicBoolean(false) }

        if (!flag.compareAndSet(false, true)) {
            //showToastLog("$flagKey уже в процессе — пропускаю")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                //showToastLog(": $flagKey старт генерации")
                createAction(mainViewModel)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                flag.set(false)
                //showToastLog(": $flagKey генерация завершена, флаг сброшен")
            }
        }
    }

    private fun showToastLog(message: String) {
        Toast.makeText(MyApplication.globalContext, message, Toast.LENGTH_SHORT).show()
        Log.d("PlaylistGen", message)
    }
}


