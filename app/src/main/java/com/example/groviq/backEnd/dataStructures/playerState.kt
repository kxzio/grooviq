package com.example.groviq.backEnd.dataStructures

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchState
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

data class playerState(

    //current state of audio player
    var currentStatus : playerStatus = playerStatus.IDLE,

    //all songs in system
    var allAudioData : MutableMap<String, songData> = mutableMapOf(),

    //keys of songs in albums, playlists. for example. use key for album (link of album) or use name of playlist as key
    var audioData     : MutableMap<String, audioSource> = mutableMapOf(),

    var playingHash   : String = ""

)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(playerState())
    val uiState: StateFlow<playerState> = _uiState

    fun setPlayerStatus(status: playerStatus) {
        _uiState.value = _uiState.value.copy(currentStatus = status)
    }

    fun setPlayingHash(songLink: String) {
        _uiState.value =_uiState.value.copy(playingHash = songLink)
    }

    fun setAlbumTracks(request: String, tracks: List<songData>) {
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

}


