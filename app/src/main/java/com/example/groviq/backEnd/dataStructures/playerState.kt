package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.playEngine.AudioPlayerManager
import com.example.groviq.backEnd.playEngine.onShuffleToogle
import com.example.groviq.backEnd.playEngine.queueElement
import com.example.groviq.backEnd.playEngine.updatePosInQueue
import com.example.groviq.backEnd.saveSystem.DataRepository
import com.example.groviq.backEnd.searchEngine.ArtistDto
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.hasInternetConnection
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class playerStatus {
    IDLE,
    PLAYING,
    PAUSE,
    LOADING_AUDIO,
    BUFFERING
}

enum class repeatMods {
    NO_REPEAT,
    REPEAT_ONE,
    REPEAT_ALL
}

data class playerState(

    //current state of audio player
    var currentStatus : playerStatus    = playerStatus.IDLE,
    var songsLoader   : Boolean         = false,

    //all songs in system
    var allAudioData : MutableMap<String, songData> = mutableMapOf(),

    //keys of songs in albums, playlists. for example. use key for album (link of album) or use name of playlist as key
    var audioData     : MutableMap<String, audioSource> = mutableMapOf(),

    //curent played data
    var playingHash              : String = "", // to indicate, whitch audio playing right now
    var playingAudioSourceHash   : String = "", // to indicate, whitch audio source is playing right now
    var searchBroserFocus        : String = "", // to not delete the data we currently see on the browser screen

    //queue info
    var currentQueue    : MutableList   < queueElement > = mutableListOf(), // the queue we have in current
    var originalQueue   : List          < queueElement > = emptyList(),     // the queue we had before the shuffle
    var shouldRebuild   : Boolean = false,                                  // indicate if we have to rebuild the queue from zero

    var posInQueue  : Int = -1,
    var lastSourceBuilded : String = "",

    //player mods
    var isShuffle   : Boolean = false,
    var repeatMode  : repeatMods = repeatMods.NO_REPEAT,

    //saved audiosources

    )


class PlayerViewModelFactory(private val repository: DataRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PlayerViewModel(repository) as T
    }
}


@UnstableApi
class PlayerViewModel(private val repository: DataRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(playerState())
    val uiState: StateFlow<playerState> = _uiState

    val playerManager = AudioPlayerManager(MyApplication.globalContext)

    fun loadAllFromRoom() {
        viewModelScope.launch {
            val (songsMap, audioMap) = withContext(Dispatchers.IO) {
                repository.loadAllAudioAndSources(MyApplication.globalContext!!)
            }
            // обновляем state на main
            _uiState.value = _uiState.value.copy(
                allAudioData = songsMap.toMutableMap(),
                audioData = audioMap.toMutableMap()
            )
        }
    }

    fun saveSongToRoom(song: songData) {
        viewModelScope.launch(Dispatchers.IO) { repository.saveSong(this@PlayerViewModel, song, MyApplication.globalContext!!) }
    }

    fun saveSongsFromSourceToRoom(string: String) {
        viewModelScope.launch(Dispatchers.IO) {

            val songs = _uiState.value.audioData[string]?.songIds
                ?.mapNotNull { _uiState.value.allAudioData[it] }
                ?: emptyList()

            songs.forEach { song ->
                repository.saveSong(this@PlayerViewModel, song, MyApplication.globalContext!!)
            }
        }
    }

    fun saveAudioSourcesToRoom() {
        viewModelScope.launch(Dispatchers.IO) { repository.saveAudioSources(this@PlayerViewModel) }
    }

    //current status of player : BUFFERING, IDLE and etc. to prevent user from not good UI decisions
    fun setPlayerStatus(status: playerStatus) {
        _uiState.value = _uiState.value.copy(currentStatus = status)
    }

    fun setSongsLoadingStatus(status : Boolean) {
        _uiState.value =_uiState.value.copy(songsLoader = status)
    }


    fun setPlayingHash(songLink: String) {
        _uiState.value =_uiState.value.copy(playingHash = songLink)
    }

    fun setPlayingAudioSourceHash(audioSourceLink: String) {
        _uiState.value =_uiState.value.copy(playingAudioSourceHash = audioSourceLink)
    }

    fun deleteUserAdds() {
        _uiState.value =_uiState.value.copy(currentQueue  = uiState.value.currentQueue .filter { it.addedByUser == false }.toMutableList())
        _uiState.value =_uiState.value.copy(originalQueue = uiState.value.originalQueue.filter { it.addedByUser == false }.toMutableList())
    }

    fun setQueue(newQueue: MutableList < queueElement > ) {
        _uiState.value =_uiState.value.copy(currentQueue = newQueue)
    }

    fun setOriginalQueue(newQueue: List < queueElement > ) {
        _uiState.value =_uiState.value.copy(originalQueue = newQueue)
    }

    fun setShouldRebuild(b : Boolean) {
        _uiState.value =_uiState.value.copy(shouldRebuild = b)
    }

    fun setPosInQueue(newPos : Int) {
        _uiState.value =_uiState.value.copy(posInQueue = newPos)
    }

    fun setLastSourceBuilded(newSource : String) {
        _uiState.value =_uiState.value.copy(lastSourceBuilded = newSource)
    }

    fun toogleShuffleMode()
    {
        _uiState.value =_uiState.value.copy(isShuffle = !_uiState.value.isShuffle )

        //rebuild the queue
        onShuffleToogle(mainViewModel = this, _uiState.value.isShuffle)
    }

    fun toogleRepeatMode()
    {
        _uiState.value =_uiState.value.copy(repeatMode =
            if (_uiState.value.repeatMode == repeatMods.NO_REPEAT)          repeatMods.REPEAT_ALL
            else if (_uiState.value.repeatMode == repeatMods.REPEAT_ALL)    repeatMods.REPEAT_ONE
            else repeatMods.NO_REPEAT)

    }

    fun setAlbumTracks(request: String, tracks: List<songData>,
                       audioSourceName      : String,
                       audioSourceArtist    : List<ArtistDto>,
                       audioSourceYear      : String,
                       //autogenerated info
                       autoGeneratedB       : Boolean = false

    ) {

        //function for browsing to add new audiosource and chain audiofiles to new audiosource
        val currentUiState = _uiState.value

        val updatedAllAudio = currentUiState.allAudioData.toMutableMap()
        for (track in tracks) {
            if (!updatedAllAudio.containsKey(track.link)) {
                updatedAllAudio[track.link] = track
            }
        }

        val updatedAudioData = currentUiState.audioData.toMutableMap()
        updatedAudioData[request] = audioSource().apply {
            songIds             = tracks.map { it.link }.toMutableList()
            nameOfAudioSource   = audioSourceName
            artistsOfAudioSource = audioSourceArtist
            yearOfAudioSource   = audioSourceYear
            //generated info
            autoGenerated       = autoGeneratedB
            timeUpdate          = if (autoGeneratedB) System.currentTimeMillis() else 0L
        }

        _uiState.value = currentUiState.copy(
            allAudioData = updatedAllAudio,
            audioData    = updatedAudioData
        )
    }

    fun toggleStrictSaveAudioSource(request: String) {
        val currentState = _uiState.value
        val updatedAudioData = currentState.audioData.toMutableMap()

        val oldValue = updatedAudioData[request]?.shouldBeSavedStrictly

        if (oldValue != null) {
            val updatedItem = updatedAudioData[request]!!.copy(
                shouldBeSavedStrictly = !oldValue
            )
            updatedAudioData[request] = updatedItem
        }

        _uiState.value = currentState.copy(audioData = updatedAudioData)
    }

    fun setTrack(request: String, song : songData,
    ) {

        //function for browsing to add new audiosource and chain audiofiles to new audiosource
        val currentUiState = _uiState.value

        val updatedAllAudio = currentUiState.allAudioData.toMutableMap()
        if (!updatedAllAudio.containsKey(request)) {
            updatedAllAudio[request] = song
        }


        val updatedAudioData = currentUiState.audioData.toMutableMap()
        updatedAudioData[request] = audioSource().apply {
            songIds             = listOf(song.link).toMutableList()
            nameOfAudioSource   = request
        }

        _uiState.value = currentUiState.copy(
            allAudioData = updatedAllAudio,
            audioData    = updatedAudioData
        )
    }


    fun updateStreamForSong(songLink: String, streamUrl: String) {

        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(stream = streamInfo(streamUrl, if (streamUrl == "") 0L else System.currentTimeMillis()))
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateDurationForSong(songLink: String, duration: Long) {

        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(duration = duration)
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun addSongToAudioSource(songLink: String, audioSource: String) {

        val currentState = _uiState.value
        val song = currentState.allAudioData[songLink] ?: return

        val updatedAudioData = currentState.audioData.toMutableMap()

        val currentEntry = updatedAudioData[audioSource]
        val currentIds = currentEntry?.songIds ?: emptyList()


        val newIds = currentIds.toMutableList().apply {
            //add to 0 position, to add the newest tracks to top
            if (!contains(songLink)) add(0, songLink)
        }

        updatedAudioData[audioSource] = audioSource(songIds = newIds)

        _uiState.value = currentState.copy(audioData = updatedAudioData)
    }


    fun removeSongFromAudioSource(songLink: String, audioSource: String) {

        val currentState = _uiState.value
        val song = currentState.allAudioData[songLink] ?: return

        val updatedAudioData = currentState.audioData.toMutableMap()

        val currentEntry = updatedAudioData[audioSource] ?: return
        val currentIds = currentEntry.songIds

        if (!currentIds.contains(songLink)) return

        val newIds = currentIds.toMutableList().apply {
            remove(songLink)
        }

        updatedAudioData[audioSource] = audioSource(songIds = newIds)

        _uiState.value = currentState.copy(audioData = updatedAudioData)
    }

    fun isAudioSourceContainsSong(songLink: String, audioSource: String): Flow<Boolean> {
        return uiState.map { state ->
            val audioSourceEntry = state.audioData[audioSource]
            audioSourceEntry?.songIds?.contains(songLink) == true
        }
    }

    fun updateStatusForSong(songLink: String, status: songProgressStatus) {
        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(progressStatus = status)
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateFileForSong(
        songLink: String, file: File?
    ) {
        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(file = file)
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateImageForSong(songLink: String, link : String) {
        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(art_link = link)
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateDownloadingProgressForSong(songLink: String, progress : Float) {
        val currentState = _uiState.value

        val updatedAllAudioData = currentState.allAudioData.toMutableMap()

        val song = updatedAllAudioData[songLink]
        if (song != null) {
            updatedAllAudioData[songLink] = song.copy(progressStatus = song.progressStatus.copy(downloadingProgress = progress))
            _uiState.value = currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    suspend fun awaitStreamUrlFor(hash: String, timeoutMs: Long = 60_000L): String? =
        withTimeoutOrNull(timeoutMs) {
            uiState
                .map { state -> state.allAudioData[hash] }
                .filterNotNull()
                .firstOrNull { song ->
                    song.stream.streamUrl.isNotEmpty() && !song.shouldGetStream()
                }
                ?.stream
                ?.streamUrl
        }

    fun clearUnusedAudioSourcedAndSongs(searchViewModel: SearchViewModel)
    {
        val currentPlayingAudioSource   = _uiState.value.playingAudioSourceHash

        //the main logic of filter motion
        //logic operation :
        // 1. audiosource playing
        // 2. song in playlist
        // 3. browser focus
        // 4. queue have song in audiosource

        val playlists    = _uiState.value.audioData.filter { !it.key.contains("https://") }

        //var for queue check
        val currentQueueAudioSources = _uiState.value.currentQueue.map { it.audioSource }.toSet()

        _uiState.value.audioData = _uiState.value.audioData.filter {

            _uiState.value.audioData[it.key]?.shouldBeSavedStrictly ?: false || //this audiosource is saved strictly
            it.key == currentPlayingAudioSource         ||                      //we play this audioSource
            playlists.containsKey(it.key)               ||                      //this audioSource is playlist
            it.key == uiState.value.searchBroserFocus   ||                      //this audiosource is UI focused
            currentQueueAudioSources.contains(it.key)   ||                      //this audiosource is used for queue building
            it.key == searchViewModel.uiState.value.currentArtist.url           //current opened artist popular songs

        }.toMutableMap()


        //dont touch this logic, it just repeat the logic of delete
        val usedSongIds = _uiState.value.audioData.values
            .flatMap { it.songIds }
            .toSet()

        _uiState.value.allAudioData = _uiState.value.allAudioData
            .filter { usedSongIds.contains(it.key) }
            .toMutableMap()

    }

    fun createAudioSource(name : String) {
        val currentState = _uiState.value

        val updatedAudioData = currentState.audioData.toMutableMap()
        updatedAudioData[name] = audioSource().apply {
            nameOfAudioSource    = name
        }

        _uiState.value = currentState.copy(
            audioData    = updatedAudioData
        )
    }

    fun isSongSavable(song: songData): Boolean {

        val playlists = getPlaylists()

        val inPlaylist = playlists.any { it.value.songIds.contains(song.link) }
        val inStrictSource = _uiState.value.audioData.any { (_, src) ->
            src.shouldBeSavedStrictly && song.link in src.songIds
        }

        return inPlaylist || inStrictSource
    }

    fun getSavableAudioSources(): Map<String, audioSource> {
        val playlists = getPlaylists()
        val strictSaved = _uiState.value.audioData.filter { it.value.shouldBeSavedStrictly }

        return playlists + strictSaved
    }

    fun isPlaylist(audioSource: String) : Boolean
    {

        val isPlaylist  = audioSource.contains("https://").not()

        return isPlaylist

    }

    fun getPlaylists() : Map<String, audioSource>
    {

        val playlists  = _uiState.value.audioData.filter { isPlaylist(it.key) }

        return playlists

    }

    fun updateBrowserHashFocus(hash: String)
    {
        _uiState.value =_uiState.value.copy(searchBroserFocus = hash )
    }

    fun waitTrackAndPlay(searchViewModel: SearchViewModel, hash: String, audioSourcePath: String) {

        if (AppViewModels.player.playerManager.player != null)
            AppViewModels.player.playerManager.player!!.stop()

        _uiState.value = _uiState.value.copy(
            playingHash = "",
            playingAudioSourceHash = ""
        )

        viewModelScope.launch {

            //wait
            val track = uiState
                .map { state -> state.allAudioData[hash] }
                .filterNotNull()
                .first()

            //start
            setPlayingAudioSourceHash(audioSourcePath)
            updatePosInQueue(this@PlayerViewModel, track.link)
            deleteUserAdds()
            AppViewModels.player.playerManager.play(track.link, this@PlayerViewModel, searchViewModel, true)
        }
    }

    fun waitAudioSoureToAppearAndPlayNext(searchViewModel: SearchViewModel, audioSourcePath: String) {

        viewModelScope.launch {

            //wait
            val source = uiState
                .map { state -> state.audioData[audioSourcePath] }
                .filterNotNull()
                .first()

            //start
            AppViewModels.player.playerManager.nextSong(mainViewModel = this@PlayerViewModel, searchViewModel)
        }
    }

    private val artListeners = mutableListOf<(String, Bitmap?) -> Unit>()

    fun addArtListener(listener: (String, Bitmap?) -> Unit) {
        artListeners.add(listener)
    }

    fun removeArtListener(listener: (String, Bitmap?) -> Unit) {
        artListeners.remove(listener)
    }

    sealed class SongArtResult {
        data class BitmapResult(val bitmap: Bitmap) : SongArtResult()
        data class UrlResult(val url: String) : SongArtResult()
    }

    suspend fun awaitSongArt(mainViewModel: PlayerViewModel, songKey: String): SongArtResult {

        val song = mainViewModel.uiState.value.allAudioData[songKey]
            ?: throw Exception("Song not found")

        song.art_local_link?.let { path ->
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) {
                return SongArtResult.BitmapResult(bmp)
            }
        }

        song.art_link?.let { artLink ->
            if (hasInternetConnection(MyApplication.globalContext)) {
                val modifiedLink = if (artLink.contains("=w")) {
                    artLink.replace(Regex("=w\\d+"), "=w200-h200")
                } else {
                    artLink
                }

                // Загружаем Bitmap в IO
                val bitmap = withContext(Dispatchers.IO) { loadBitmapFromUrl(modifiedLink) }
                bitmap?.let {
                    val scaled = Bitmap.createScaledBitmap(it, 200, 200, true)
                    return SongArtResult.BitmapResult(scaled)
                }
            }
        }

        return suspendCancellableCoroutine { cont ->
            val listener = object : (String, Bitmap?) -> Unit {
                override fun invoke(key: String, bitmap: Bitmap?) {
                    if (key == songKey && bitmap != null) {
                        mainViewModel.removeArtListener(this)
                        val scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                        cont.resume(SongArtResult.BitmapResult(scaled)) {}
                    }
                }
            }

            mainViewModel.addArtListener(listener)

            cont.invokeOnCancellation {
                mainViewModel.removeArtListener(listener)
            }
        }
    }




}


