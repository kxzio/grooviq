package com.example.groviq.backEnd.dataStructures

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import com.example.groviq.backEnd.playEngine.onShuffleToogle
import com.example.groviq.backEnd.playEngine.queueElement
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

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
    var currentStatus : playerStatus = playerStatus.IDLE,

    //all songs in system
    var allAudioData : MutableMap<String, songData> = mutableMapOf(),

    //keys of songs in albums, playlists. for example. use key for album (link of album) or use name of playlist as key
    var audioData     : MutableMap<String, audioSource> = mutableMapOf(),

    //curent played data
    var playingHash              : String = "", // to indicate, whitch audio playing right now
    var playingAudioSourceHash   : String = "", // to indicate, whitch audio source is playing right now
    var searchBroserFocus        : String = "", // to not delete the data we currently see on the browser screen

    //queue info
    var currentQueue    : MutableList   < queueElement > = mutableListOf(),
    val originalQueue   : List          < queueElement > = emptyList(), // for shuffle mode

    var posInQueue  : Int = -1,
    var lastSourceBuilded : String = "",

    //player mods
    var isShuffle   : Boolean = false,
    var repeatMode  : repeatMods = repeatMods.NO_REPEAT,

    )

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(playerState())
    val uiState: StateFlow<playerState> = _uiState

    //current status of player : BUFFERING, IDLE and etc. to prevent user from not good UI decisions
    fun setPlayerStatus(status: playerStatus) {
        _uiState.value = _uiState.value.copy(currentStatus = status)
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
        onShuffleToogle(mainViewModel = this)
    }

    fun toogleRepeatMode()
    {
        _uiState.value =_uiState.value.copy(repeatMode =
            if (_uiState.value.repeatMode == repeatMods.NO_REPEAT)          repeatMods.REPEAT_ALL
            else if (_uiState.value.repeatMode == repeatMods.REPEAT_ALL)    repeatMods.REPEAT_ONE
            else repeatMods.NO_REPEAT)

    }

    fun setAlbumTracks(request: String, tracks: List<songData>) {

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
            songIds = tracks.map { it.link }.toMutableList()
        }

        _uiState.value = currentUiState.copy(
            allAudioData = updatedAllAudio,
            audioData = updatedAudioData
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

    suspend fun awaitStreamUrlFor(hash: String): String? {
        return uiState
            .map { state -> state.allAudioData[hash]?.stream?.streamUrl?.takeIf { it.isNotEmpty() } }
            .filterNotNull()
            .firstOrNull()
    }

    fun clearUnusedAudioSourcedAndSongs()
    {
        val currentPlayingAudioSource   = _uiState.value.playingAudioSourceHash

        //the main logic of filter motion
        //logic operation :
        // 1. audiosource playing
        // 2. song in playlist
        // 3. browser focus
        // 4. queue have song in audiosource

        val playlists    = _uiState.value.audioData.filter { !it.key.contains("http") }

        //var for queue check
        val currentQueueAudioSources = _uiState.value.currentQueue.map { it.audioSource }.toSet()

        _uiState.value.audioData = _uiState.value.audioData.filter {


            it.key == currentPlayingAudioSource         ||      //we play this audioSource
            playlists.containsKey(it.key)               ||      //this audioSource is playlist
            it.key == uiState.value.searchBroserFocus   ||      //this audiosource is UI focused
            currentQueueAudioSources.contains(it.key)           //this audiosource is used for queue building

        }.toMutableMap()


        //dont touch this logic, it just repeat the logic of delete
        val usedSongIds = _uiState.value.audioData.values
            .flatMap { it.songIds }
            .toSet()

        _uiState.value.allAudioData = _uiState.value.allAudioData
            .filter { usedSongIds.contains(it.key) }
            .toMutableMap()


    }

    fun updateBrowserHashFocus(hash: String)
    {
        _uiState.value =_uiState.value.copy(searchBroserFocus = hash )
    }

}


