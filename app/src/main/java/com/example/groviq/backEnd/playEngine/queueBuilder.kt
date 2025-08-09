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

    // Словарь базовых песен для быстрого создания queueElement по hashKey
    val baseMap = baseSongs.associateBy { it.link }

    val newQueue = mutableListOf<queueElement>()

    // Множество уже добавленных базовых песен, чтобы не добавить дубликаты
    val addedBaseHashes = mutableSetOf<String>()

    // Идём по старому originalQueue и вставляем элементы в новый список
    for (elem in oldOriginal) {
        if (elem.addedByUser) {
            // Пользовательский трек — просто вставляем на место
            newQueue.add(elem)
        } else {
            // Базовый трек
            if (baseHashSet.contains(elem.hashKey) && !addedBaseHashes.contains(elem.hashKey)) {
                // Создаём queueElement из актуальных данных (без addedByUser)
                val song = baseMap[elem.hashKey]!!
                newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash))
                addedBaseHashes.add(elem.hashKey)
            }
            // Если базовый трек не найден в новом списке, не добавляем — его возможно убрали из источника
        }
    }

    // Добавляем базовые треки, которые не были добавлены (новые в источнике)
    for (song in baseSongs) {
        if (!addedBaseHashes.contains(song.link)) {
            newQueue.add(queueElement(hashKey = song.link, audioSource = view.playingAudioSourceHash))
            addedBaseHashes.add(song.link)
        }
    }

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
        // запасной план: удалить первое подходящее по тройке полей (если id нет)
        val fallback = newOriginal.indexOfFirst { it.hashKey == target.hashKey && it.audioSource == target.audioSource && it.addedByUser == target.addedByUser }
        if (fallback != -1) newOriginal.removeAt(fallback)
    }
    mainViewModel.setOriginalQueue(newOriginal)

    // корректировка позиции
    val newPos = when {
        view.currentQueue.isEmpty() -> 0
        view.posInQueue == currentIndex -> currentIndex.coerceAtMost(newCurrent.lastIndex.coerceAtLeast(0))
        view.posInQueue > currentIndex -> (view.posInQueue - 1)
        else -> view.posInQueue
    }
    mainViewModel.setPosInQueue(newPos)
    mainViewModel.setShouldRebuild(true)
}
fun moveInQueue     (mainViewModel: PlayerViewModel, fromIndex: Int, toIndex: Int) {
    val view = mainViewModel.uiState.value
    if (fromIndex !in view.currentQueue.indices) return
    if (toIndex !in 0..view.currentQueue.lastIndex) return
    if (fromIndex == toIndex) return

    // 1) currentQueue
    val newCurrent = view.currentQueue.toMutableList()
    val elem = newCurrent.removeAt(fromIndex)
    newCurrent.add(toIndex, elem)
    mainViewModel.setQueue(newCurrent)

    // 2) originalQueue — перемещаем соответствующий элемент
    val newOriginal = view.originalQueue.toMutableList()
    val origFrom = newOriginal.indexOfFirst { it.id == elem.id }

    if (origFrom != -1) {
        val elemOrig = newOriginal.removeAt(origFrom)

        // найти "соседа" в новой currentQueue, чтобы понять, куда вставить в originalQueue
        val successor = newCurrent.getOrNull(toIndex + 1) // элемент, который теперь идёт после перемещённого (если есть)
        val insertBefore = if (successor != null) {
            newOriginal.indexOfFirst { it.id == successor.id }.let { if (it == -1) newOriginal.size else it }
        } else {
            newOriginal.size // вставляем в конец
        }

        // если мы удалили элемент слева от целевой позиции, индекс вставки смещается на -1
        val adjustedInsert = if (origFrom < insertBefore) (insertBefore - 1).coerceIn(0, newOriginal.size) else insertBefore.coerceIn(0, newOriginal.size)
        newOriginal.add(adjustedInsert, elemOrig)
        mainViewModel.setOriginalQueue(newOriginal)
    } else {
        // fallback: если не нашли по id — ничего не делаем или пытаемся найти по тройке полей (редкий случай)
    }

    // 3) корректируем posInQueue
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
