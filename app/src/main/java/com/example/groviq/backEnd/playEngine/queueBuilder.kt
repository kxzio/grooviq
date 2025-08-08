package com.example.groviq.backEnd.playEngine

import com.example.groviq.backEnd.dataStructures.PlayerViewModel

data class queueElement(
    val hashKey     : String,
    val audioSource : String,
    val addedByUser : Boolean = false
)


fun createQueueOnAudioSourceHash(mainViewModel : PlayerViewModel, requstedHash : String)
{

    val view = mainViewModel.uiState.value

    if (mainViewModel.uiState.value.allAudioData[requstedHash] == null)
        return

    val songsHashesInSource = mainViewModel.uiState.value.audioData[view.playingAudioSourceHash]!!.songIds

    var sortedSongs = songsHashesInSource
        .mapNotNull { songId -> mainViewModel.uiState.value.allAudioData[songId] }

    val queue: MutableList<queueElement> = sortedSongs.mapIndexed { index, song ->
        queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash )
    }.toMutableList()

    mainViewModel.setOriginalQueue(queue.toList())
    mainViewModel.setLastSourceBuilded(view.playingAudioSourceHash)

    val originalQueue = mainViewModel.uiState.value.originalQueue.toMutableList()

    //reconstruct queue for shuffle or cancel shuffling and return original queue
    val newQueue = if (view.isShuffle) {

        var withoutCurrent = originalQueue.toMutableList()
        withoutCurrent.removeIf { it.hashKey == requstedHash }

        var newShuffled = withoutCurrent.shuffled().toMutableList()
        newShuffled.add(0, queueElement(requstedHash, view.playingAudioSourceHash))

        newShuffled

    } else {
        originalQueue
    }

    mainViewModel.setQueue(newQueue)
    mainViewModel.setPosInQueue( newQueue.indexOfFirst { it.hashKey == requstedHash } )
}

fun updatePosInQueue(mainViewModel : PlayerViewModel, hash : String)
{
    //dont update if queue is empty
    if (mainViewModel.uiState.value.currentQueue.isEmpty())
        return

    //dont update pos in queue, because queue is not updated and still can be about old data
    if (mainViewModel.uiState.value.lastSourceBuilded != mainViewModel.uiState.value.playingAudioSourceHash)
        return

    //clear all user added requests
    val base = mainViewModel.uiState.value.originalQueue.toMutableList()
    val rebuilt = if (mainViewModel.uiState.value.isShuffle)
        base.shuffled()
    else
        base

    mainViewModel.setQueue(rebuilt.toMutableList())
    mainViewModel.setPosInQueue(rebuilt.indexOfFirst { it.hashKey == hash })

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

fun addToCurrentQueue(
    mainViewModel: PlayerViewModel,
    hash: String,
    audioSourceFrom: String
) {
    val view = mainViewModel.uiState.value

    // если очередь пуста — выходим
    if (view.currentQueue.isEmpty()) return

    val elem = queueElement(hash, audioSourceFrom, addedByUser = true)
    val basePos = view.posInQueue

    // 1) считаем, сколько user-added песен уже стоит после текущей позиции
    val existingAddsAfter = view.currentQueue
        .drop(basePos + 1)
        .count { it.addedByUser }

    // 2) вычисляем индекс вставки в currentQueue: сразу после current + смещение
    val insertPosCurrent = basePos + 1 + existingAddsAfter
    view.currentQueue.add(insertPosCurrent, elem)

    // 3) аналогично для originalQueue (чтобы вставки не терялись при shuffle/пересборке)
    val origList = view.originalQueue.toMutableList()
    val existingOrigAddsAfter = origList
        .drop(basePos + 1)
        .count { it.addedByUser }
    val insertPosOriginal = basePos + 1 + existingOrigAddsAfter
    origList.add(insertPosOriginal, elem)
    mainViewModel.setOriginalQueue(origList)
}
