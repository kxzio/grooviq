package com.example.groviq.backEnd.playEngine

import com.example.groviq.backEnd.dataStructures.PlayerViewModel

data class queueElement(
    val hashKey     : String,
    val audioSource : String,
    val addedByUser : Boolean = false,
    val id          : String  = java.util.UUID.randomUUID().toString(),
)

fun createQueueOnAudioSourceHash(mainViewModel: PlayerViewModel, requstedHash: String) {
    val view = mainViewModel.uiState.value

    if (mainViewModel.uiState.value.allAudioData[requstedHash] == null)
        return

    val baseSongs = mainViewModel.uiState.value.audioData[view.playingAudioSourceHash]!!.songIds
        .mapNotNull { songId -> mainViewModel.uiState.value.allAudioData[songId] }

    val baseHashSet = baseSongs.map { it.link }.toSet()

    val oldOriginal = mainViewModel.uiState.value.originalQueue

    // the map of all base songs by hash
    val baseMap = baseSongs.associateBy { it.link }

    val newQueue = mutableListOf<queueElement>()

    //already added hashes
    val addedBaseHashes = mutableSetOf<String>()

    //take original queue and take used ADDED requests
    for (elem in oldOriginal) {
        if (elem.addedByUser) {
            //user added
            newQueue.add(elem)
        } else {
            //base track
            if (baseHashSet.contains(elem.hashKey) && !addedBaseHashes.contains(elem.hashKey)) {
                //create base track
                val song = baseMap[elem.hashKey]!!
                newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash))
                addedBaseHashes.add(elem.hashKey)
            }
            //if base track not found - dont touch
        }
    }

    //add all songs that belong to this source
    for (song in baseSongs) {
        if (!addedBaseHashes.contains(song.link)) {
            newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash))
            addedBaseHashes.add(song.link)
        }
    }

    //save original queue
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

    //set queue
    mainViewModel.setQueue(newCurrentQueue)
    mainViewModel.setPosInQueue(newCurrentQueue.indexOfFirst { it.hashKey == requstedHash })
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

fun toggleShuffle(mainViewModel: PlayerViewModel, isShuffle : Boolean) {
    val view = mainViewModel.uiState.value
    val current = view.currentQueue.toMutableList()

    val newQueue = if (isShuffle) {
        //shuffle current
        val shuffled = current.shuffled().toMutableList()
        val index = shuffled.indexOfFirst { it.id == current[view.posInQueue].id }
        if (index > 0) {
            val elem = shuffled.removeAt(index)
            shuffled.add(0, elem)
        }
        shuffled
    } else {
        //save added by user
        val ordered = view.originalQueue.toMutableList()
        ordered
    }

    mainViewModel.setQueue(newQueue)
    mainViewModel.setPosInQueue(0)
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
}
fun moveInQueue(mainViewModel: PlayerViewModel, fromIndex: Int, toIndex: Int) {
    val view = mainViewModel.uiState.value
    if (fromIndex !in view.currentQueue.indices) return
    if (toIndex !in 0..view.currentQueue.lastIndex) return
    if (fromIndex == toIndex) return

    // Работаем с currentQueue
    val newCurrent = view.currentQueue.toMutableList()
    val elem = newCurrent.removeAt(fromIndex)
    newCurrent.add(toIndex, elem)
    mainViewModel.setQueue(newCurrent)

    // Работаем с originalQueue — перемещаем на ту же позицию toIndex
    val newOriginal = view.originalQueue.toMutableList()
    val origFrom = newOriginal.indexOfFirst { it.id == elem.id }
    if (origFrom != -1) {
        val elemOrig = newOriginal.removeAt(origFrom)
        val insertPos = toIndex.coerceIn(0, newOriginal.size)
        newOriginal.add(insertPos, elemOrig)
        mainViewModel.setOriginalQueue(newOriginal)
    }

    // Корректируем posInQueue
    val currentPos = view.posInQueue
    val newPos = when {
        currentPos == fromIndex -> toIndex
        fromIndex < currentPos && toIndex >= currentPos -> currentPos - 1
        fromIndex > currentPos && toIndex <= currentPos -> currentPos + 1
        else -> currentPos
    }
    mainViewModel.setPosInQueue(newPos)
    mainViewModel.setShouldRebuild(true)
}
