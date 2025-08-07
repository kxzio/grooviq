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

    mainViewModel.setOriginalQueue(queue.toList())

    val originalQueue = mainViewModel.uiState.value.originalQueue.toMutableList()

    val newQueue = if (view.isShuffle) {

        var withoutCurrent = originalQueue.toMutableList()
        withoutCurrent.remove(requstedHash)

        var newShuffled = withoutCurrent.shuffled().toMutableList()
        newShuffled.add(0, requstedHash)

        newShuffled

    } else {
        originalQueue
    }

    mainViewModel.setQueue(newQueue)
    mainViewModel.setPosInQueue( newQueue.indexOfFirst { it == requstedHash } )
}

fun updatePosInQueue(mainViewModel : PlayerViewModel, hash : String)
{
    //dont update if queue is empty
    if (mainViewModel.uiState.value.currentQueue.isEmpty())
        return

    //dont update pos in queue, because queue is not updated and still can be about old data
    if (mainViewModel.uiState.value.lastSourceBuilded != mainViewModel.uiState.value.playingAudioSourceHash)
        return


    mainViewModel.setPosInQueue(  mainViewModel.uiState.value.currentQueue.indexOfFirst { it == hash } )
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

fun onShuffleToogle(mainViewModel: PlayerViewModel)
{
    createQueueOnAudioSourceHash(mainViewModel, mainViewModel.uiState.value.playingHash)
}
