package com.example.groviq.backEnd.playEngine

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.streamProcessor.currentFetchJob
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
import com.example.groviq.playerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AudioPlayerManager(context: Context) {

    //main player
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    //thread controllers
    private var currentPlaybackJob: Job? = null
    private val playbackDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PlaybackThread").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val playbackScope = CoroutineScope(playbackDispatcher + SupervisorJob())


    //cooldown flag for play functions
    @Volatile
    private var lastPlayTimeMs = 0L
    private val PLAY_COOLDOWN_MS = 10L

    fun play(hashkey : String, mainViewModel: PlayerViewModel, userPressed : Boolean = false) {


        //check bounding box
        if (mainViewModel.uiState.value.allAudioData[hashkey] == null)
            return

        //check cooldown
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimeMs < PLAY_COOLDOWN_MS) return
        lastPlayTimeMs = now

        player.stop()

        //if we start playing another track, but last song didnt get the stream yet, we should cancel the thread to not overload
        if (hashkey != mainViewModel.uiState.value.playingHash) {

            //cancel all threads
            currentPlaybackJob?.cancel()
            currentFetchJob   ?.cancel()
            mainViewModel.updateStatusForSong(
                hashkey,
                mainViewModel.uiState.value.allAudioData[hashkey]!!.progressStatus.copy(streamHandled = false)
            )
        }

        //current playing index in hash value
        mainViewModel.setPlayingHash(hashkey)

        //clear all songs that we had by surfing the web, now we have to delete them, because they have no clue, since user played song
        mainViewModel.clearUnusedAudioSourcedAndSongs()

        setSongProgress(0f, 0L)

        if (mainViewModel.uiState.value.shouldRebuild && userPressed)
        {
            //reset all
            mainViewModel.uiState.value.currentQueue    = mutableListOf()
            mainViewModel.uiState.value.originalQueue   = emptyList()

            //if queue was changed and user pressed, we have to recover original queue
            createQueueOnAudioSourceHash(mainViewModel, hashkey)
            mainViewModel.setShouldRebuild(false)
        }
        else if (mainViewModel.uiState.value.lastSourceBuilded != mainViewModel.uiState.value.playingAudioSourceHash ||
            (userPressed && mainViewModel.uiState.value.isShuffle))
        {
            //if the audio source changed, we have to rebuild the queue
            createQueueOnAudioSourceHash(mainViewModel, hashkey)
            //update last built audio source
            mainViewModel.setLastSourceBuilded(mainViewModel.uiState.value.playingAudioSourceHash)
        }

        currentPlaybackJob = playbackScope.launch {

            val song = mainViewModel.uiState.value.allAudioData[hashkey] ?: return@launch

            if (song!!.shouldGetStream()) {

                if (song.progressStatus.streamHandled.not()) {
                    //stream not handled yet by any thread, we should get it by yourself

                    //clear old stream if we had one
                    mainViewModel.updateStreamForSong(
                        hashkey,
                        ""
                    )
                    //request to get the new one
                    fetchAudioStream(mainViewModel, hashkey)
                }

            }

            //reactive waiting for stream url
            val streamUrl = mainViewModel.awaitStreamUrlFor(hashkey)

            if (streamUrl != null) {
                //back to UI layer
                withContext(Dispatchers.Main)
                {
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                }
            }


        }

    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        player.release()
    }

    fun nextSong(mainViewModel: PlayerViewModel) {
        val view = mainViewModel.uiState.value

        val repeatMode = view.repeatMode
        val isShuffle = view.isShuffle
        val currentQueue = view.currentQueue
        val originalQueue = view.originalQueue
        val pos = view.posInQueue

        if (repeatMode == repeatMods.REPEAT_ONE) {
            // Повтор одного трека
            playerManager.play(currentQueue[pos].hashKey, mainViewModel)
            return
        }

        val nextIndex = pos + 1

        if (nextIndex < currentQueue.size) {
            moveToNextPosInQueue(mainViewModel)
            val newPos = mainViewModel.uiState.value.posInQueue
            playerManager.play(currentQueue[newPos].hashKey, mainViewModel)
            return
        }

        // reached end of queue
        if (repeatMode == repeatMods.REPEAT_ALL) {
            val newQueue = if (isShuffle) {
                originalQueue.shuffled().toMutableList()
            } else {
                originalQueue.toMutableList()
            }

            mainViewModel.setQueue(newQueue)
            mainViewModel.setPosInQueue(0)
            playerManager.play(newQueue[0].hashKey, mainViewModel)
            return
        }

        // Если NO_REPEAT и конец очереди — ничего не делаем
    }

    fun prevSong(mainViewModel: PlayerViewModel)
    {
        //it means, we dont move to pres song if we have no prev song
        if (mainViewModel.uiState.value.posInQueue - 1 < 0)
            return

        //move the index of queue pos
        moveToPrevPosInQueue(mainViewModel)

        //play index
        val viewState = mainViewModel.uiState.value

        if (viewState.currentQueue.isEmpty() ||
            viewState.posInQueue !in viewState.currentQueue.indices) return

        play(viewState.currentQueue[viewState.posInQueue].hashKey, mainViewModel)
    }

    fun isPlaying(): Boolean = player.isPlaying
}