package com.example.groviq.backEnd.playEngine

import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.service.nextSongHashPending

//the requst of navigation artist
val isQueueInBuildingProcess = mutableStateOf<Boolean>(false)


data class queueElement(
    val hashKey     : String,
    val audioSource : String,
    val addedByUser : Boolean = false,
    val id          : String  = java.util.UUID.randomUUID().toString(),
)

fun createQueueOnAudioSourceHash(mainViewModel: PlayerViewModel, requstedHash: String) {

    isQueueInBuildingProcess.value = true

    try {
        val view = mainViewModel.uiState.value

        if (mainViewModel.uiState.value.allAudioData[requstedHash] == null)
            return

        val baseSongs = mainViewModel.uiState.value.audioData[view.playingAudioSourceHash]?.songIds
            ?.mapNotNull { songId -> mainViewModel.uiState.value.allAudioData[songId] } ?: emptyList()

        val baseHashSet = baseSongs.map { it.link }.toSet()

        val oldOriginal = mainViewModel.uiState.value.originalQueue

        // map existing original elements by hashKey -> to reuse ids
        val oldByHash = oldOriginal.associateBy { it.hashKey }

        // the map of all base songs by hash
        val baseMap = baseSongs.associateBy { it.link }

        val newQueue = mutableListOf<queueElement>()

        // already added hashes
        val addedBaseHashes = mutableSetOf<String>()

        // take original queue and take used ADDED requests (and reuse ids for base tracks)
        for (elem in oldOriginal) {
            if (elem.addedByUser) {
                // user-added — сохраняем как есть (и id)
                newQueue.add(elem)
            } else {
                // base track
                if (baseHashSet.contains(elem.hashKey) && !addedBaseHashes.contains(elem.hashKey)) {
                    val song = baseMap[elem.hashKey]!!
                    // попробуем взять существующий id для этого hash из старой очереди
                    val existing = oldByHash[song.link]
                    val idToUse = existing?.id ?: java.util.UUID.randomUUID().toString()
                    newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash, id = idToUse))
                    addedBaseHashes.add(elem.hashKey)
                }
                // если base track не найден — пропускаем
            }
        }

        // add all songs that belong to this source (если какие-то остались)
        for (song in baseSongs) {
            if (!addedBaseHashes.contains(song.link)) {
                val existing = oldByHash[song.link]
                val idToUse = existing?.id ?: java.util.UUID.randomUUID().toString()
                newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash, id = idToUse))
                addedBaseHashes.add(song.link)
            }
        }

        // save original queue
        mainViewModel.setOriginalQueue(newQueue)
        mainViewModel.setLastSourceBuilded(view.playingAudioSourceHash)

        val originalQueue = mainViewModel.uiState.value.originalQueue.toMutableList()

        val newCurrentQueue = if (view.isShuffle) {
            val shuffled = originalQueue.shuffled().toMutableList()
            val index = shuffled.indexOfFirst { it.hashKey == requstedHash }
            if (index > 0) {
                val elem = shuffled.removeAt(index)
                shuffled.add(0, elem)
            }
            shuffled
        } else {
            originalQueue
        }

        // set queue
        mainViewModel.setQueue(newCurrentQueue)
        // set position to the element that matches requested hash (fallback to 0 if not found)
        mainViewModel.setPosInQueue(newCurrentQueue.indexOfFirst { it.hashKey == requstedHash }.takeIf { it >= 0 } ?: 0)
        updateNextSongHash(mainViewModel)

    } finally {
        isQueueInBuildingProcess.value = false
    }

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
    updateNextSongHash(mainViewModel)

}

fun moveToNextPosInQueue(mainViewModel : PlayerViewModel)
{
    if (mainViewModel.uiState.value.posInQueue + 1 >= mainViewModel.uiState.value.currentQueue.size)
        return

    mainViewModel.setPosInQueue(  mainViewModel.uiState.value.posInQueue + 1 )
    updateNextSongHash(mainViewModel)
}

fun moveToPrevPosInQueue(mainViewModel : PlayerViewModel)
{
    if (mainViewModel.uiState.value.posInQueue - 1 < 0)
        return

    mainViewModel.setPosInQueue(  mainViewModel.uiState.value.posInQueue - 1 )
    updateNextSongHash(mainViewModel)
}

fun toggleShuffle(mainViewModel: PlayerViewModel, isShuffle : Boolean) {

    isQueueInBuildingProcess.value = true

    try {
        val view = mainViewModel.uiState.value
        val current = view.currentQueue.toMutableList()
        val currentSong = current.getOrNull(view.posInQueue) ?: return

        val newQueue = if (isShuffle) {
            // shuffle current, but keep currentSong as first (ищем по id)
            val shuffled = current.shuffled().toMutableList()
            val index = shuffled.indexOfFirst { it.id == currentSong.id }
            if (index > 0) {
                val elem = shuffled.removeAt(index)
                shuffled.add(0, elem)
            }
            shuffled
        } else {
            // restore original queue and set position to the same element (ищем по id)
            val ordered = view.originalQueue.toMutableList()
            val idx = ordered.indexOfFirst { it.id == currentSong.id }
            mainViewModel.setQueue(ordered)
            mainViewModel.setPosInQueue(if (idx >= 0) idx else 0)
            updateNextSongHash(mainViewModel)
            return
        }

        mainViewModel.setQueue(newQueue)
        // after shuffling we put current at 0
        mainViewModel.setPosInQueue(0)
        updateNextSongHash(mainViewModel)

    } finally {
        isQueueInBuildingProcess.value = false
    }

}

fun onShuffleToogle(mainViewModel: PlayerViewModel, isShuffle : Boolean)
{
    toggleShuffle(mainViewModel, isShuffle)
}

fun addToCurrentQueue(
    mainViewModel: PlayerViewModel,
    hash: String,
    audioSourceFrom: String
) {
    val view = mainViewModel.uiState.value
    if (view.currentQueue.isEmpty()) return

    val elem = queueElement(id = java.util.UUID.randomUUID().toString(), hashKey = hash, audioSource = audioSourceFrom, addedByUser = true)
    val basePos = view.posInQueue

    val existingAddsAfter = view.currentQueue
        .drop(basePos + 1)
        .count { it.addedByUser }

    val insertPosCurrent = basePos + 1 + existingAddsAfter
    val newCurrent = view.currentQueue.toMutableList().apply { add(insertPosCurrent, elem) }
    mainViewModel.setQueue(newCurrent)

    val origList = view.originalQueue.toMutableList()
    val existingOrigAddsAfter = origList
        .drop(basePos + 1)
        .count { it.addedByUser }
    val insertPosOriginal = basePos + 1 + existingOrigAddsAfter
    origList.add(insertPosOriginal.coerceIn(0, origList.size), elem)
    mainViewModel.setOriginalQueue(origList)
    updateNextSongHash(mainViewModel)
}

fun removeFromQueue (mainViewModel: PlayerViewModel, currentIndex: Int) {
    val view = mainViewModel.uiState.value
    if (currentIndex !in view.currentQueue.indices) return

    val target = view.currentQueue[currentIndex]
    val newCurrent = view.currentQueue.toMutableList().apply { removeAt(currentIndex) }
    mainViewModel.setQueue(newCurrent)

    val newOriginal = view.originalQueue.toMutableList()
    val origIndex = newOriginal.indexOfFirst { it.id == target.id }
    if (origIndex != -1) {
        newOriginal.removeAt(origIndex)
    } else {
        //if id delete was filed, we can delete it by hash, but this way is bad
        val fallback = newOriginal.indexOfFirst { it.hashKey == target.hashKey && it.audioSource == target.audioSource && it.addedByUser == target.addedByUser }
        if (fallback != -1) newOriginal.removeAt(fallback)
    }
    mainViewModel.setOriginalQueue(newOriginal)

    //fix position in queue
    val newPos = when {
        view.currentQueue.isEmpty() -> 0
        view.posInQueue == currentIndex -> currentIndex.coerceAtMost(newCurrent.lastIndex.coerceAtLeast(0))
        view.posInQueue > currentIndex -> (view.posInQueue - 1)
        else -> view.posInQueue
    }
    mainViewModel.setPosInQueue(newPos)
    mainViewModel.setShouldRebuild(true)
    updateNextSongHash(mainViewModel)
}

fun moveInQueue(mainViewModel: PlayerViewModel, fromIndex: Int, toIndex: Int) {
    val view = mainViewModel.uiState.value
    if (fromIndex !in view.currentQueue.indices) return
    if (toIndex !in 0..view.currentQueue.lastIndex) return
    if (fromIndex == toIndex) return

    //edit current queue
    val newCurrent = view.currentQueue.toMutableList()
    val elem = newCurrent.removeAt(fromIndex)
    newCurrent.add(toIndex, elem)
    mainViewModel.setQueue(newCurrent)

    //fix original queue
    val newOriginal = view.originalQueue.toMutableList()
    val origFrom = newOriginal.indexOfFirst { it.id == elem.id }
    if (origFrom != -1) {
        val elemOrig = newOriginal.removeAt(origFrom)
        val insertPos = toIndex.coerceIn(0, newOriginal.size)
        newOriginal.add(insertPos, elemOrig)
        mainViewModel.setOriginalQueue(newOriginal)
    }

    //fix pos in queue
    val currentPos = view.posInQueue
    val newPos = when {
        currentPos == fromIndex -> toIndex
        fromIndex < currentPos && toIndex >= currentPos -> currentPos - 1
        fromIndex > currentPos && toIndex <= currentPos -> currentPos + 1
        else -> currentPos
    }
    mainViewModel.setPosInQueue(newPos)
    mainViewModel.setShouldRebuild(true)
    updateNextSongHash(mainViewModel)
}

fun updateNextSongHash(mainViewModel: PlayerViewModel) {

    val uiState = mainViewModel.uiState.value
    val queue = uiState.currentQueue

    val currentPos = uiState.posInQueue ?: return
    val nextPos = currentPos + 1

    if (queue.isNullOrEmpty() || nextPos >= queue.size) return

    val nextSong = queue[nextPos] ?: return
    nextSongHashPending.value = nextSong.hashKey

    val player = AppViewModels.player.playerManager.player
    val currentIndex = player.currentMediaItemIndex

    addTrackToMediaItems?.cancel()

    if (currentIndex != C.INDEX_UNSET) {
        while (player.mediaItemCount > currentIndex + 1) {
            player.removeMediaItem(currentIndex + 1)
        }
    }

}
