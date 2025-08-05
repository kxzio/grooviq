package com.example.groviq.backEnd.playEngine

import com.example.groviq.backEnd.dataStructures.PlayerViewModel

fun createQueueOnAudioSourceHash(mainViewModel : PlayerViewModel, requstedHash : String)
{

    val view = mainViewModel.uiState.value

    if (mainViewModel.uiState.value.allAudioData[requstedHash] == null)
        return

    val songsHashesInSource = mainViewModel.uiState.value.audioData[view.playingAudioSourceHash]!!.songIds

    val sortedSongs = songsHashesInSource
        .mapNotNull { songId -> mainViewModel.uiState.value.allAudioData[songId] }
        .sortedBy { it.number }

    var queue: MutableList<String> = sortedSongs.map { it.link }.toMutableList()

    mainViewModel.setQueue(queue)
    mainViewModel.setPosInQueue( queue.indexOfFirst { it == requstedHash } )
}

fun moveToNextPosInQueue(mainViewModel : PlayerViewModel)
{
    if (mainViewModel.uiState.value.posInQueue + 1 >= mainViewModel.uiState.value.currentQueue.size)
        return

    mainViewModel.setPosInQueue(  mainViewModel.uiState.value.posInQueue + 1 )
}

fun moveToPrevPosInQueue(mainViewModel : PlayerViewModel)
{
    if (mainViewModel.uiState.value.posInQueue - 1 < 0)
        return

    mainViewModel.setPosInQueue(  mainViewModel.uiState.value.posInQueue - 1 )
}
