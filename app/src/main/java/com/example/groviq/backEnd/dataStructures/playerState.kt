package com.example.groviq.backEnd.dataStructures

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class playerStatus {
    IDLE,
    PLAYING,
    PAUSE,
    LOADING_AUDIO,
}

data class playerState(

    //current state of audio player
    var currentStatus : playerStatus = playerStatus.IDLE,

    //all songs in system
    var allAudioData : MutableMap<String, songData> = mutableMapOf(),

    //keys of songs in albums, playlists. for example. use key for album (link of album) or use name of playlist as key
    val audioData     : MutableMap<String, audioSource> = mutableMapOf()
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(playerState())
    val uiState: StateFlow<playerState> = _uiState


    fun setAlbumTracks(request: String, tracks: List<songData>) {

        val newAll = _uiState.value.allAudioData.toMutableMap()
        tracks.forEach { newAll[it.link] = it }

        val newAudioData = _uiState.value.audioData.toMutableMap()
        newAudioData[request] = audioSource().apply {
            songIds = tracks.map { it.link }.toMutableList()
        }

        _uiState.value = _uiState.value.copy(
            allAudioData = newAll,
            audioData   = newAudioData
        )
    }
}