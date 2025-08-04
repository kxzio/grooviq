package com.example.groviq.backEnd.playEngine

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.streamProcessor.currentFetchJob
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
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

    fun play(hashkey : String, mainViewModel: PlayerViewModel) {

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
        }

        //current playing index in hash value
        mainViewModel.setPlayingHash(hashkey)

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

    fun isPlaying(): Boolean = player.isPlaying
}