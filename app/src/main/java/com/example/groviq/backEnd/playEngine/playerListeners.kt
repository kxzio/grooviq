package com.example.groviq.backEnd.playEngine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.currentRelatedTracksJob
import com.example.groviq.backEnd.streamProcessor.fetchQueueStream
import com.example.groviq.bitmapToCompressedBytes
import com.example.groviq.service.nextSongHashPending
import com.example.groviq.service.pendingDirection
import com.example.groviq.service.songPendingIntentNavigationDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Handler

private var attachedPlayer      : Player? = null
private var attachedListener    : Player.Listener? = null
private var snapshotJob         : kotlinx.coroutines.Job? = null

var trackEndingHandled = false

@OptIn(
    UnstableApi::class
)
fun createListeners(
    searchViewModel: SearchViewModel, //search view
    mainViewModel: PlayerViewModel, //player view
)
{
    val player = AppViewModels.player.playerManager.player ?: return

    if (attachedPlayer === player) return

    attachedListener?.let { old ->
        attachedPlayer?.removeListener(old)
    }
    snapshotJob?.cancel()
    snapshotJob = null

    val boundPlayer = player

    val listener = object : Player.Listener {

        private val progressHandler = android.os.Handler(Looper.getMainLooper())
        private var progressRunnable: Runnable? = null

        private fun startProgressHandler() {
            // guard to avoid double-posting
            if (progressRunnable != null) return
            progressRunnable = object : Runnable {
                override fun run() {
                    try {
                        val position = boundPlayer.currentPosition
                        val duration = boundPlayer.duration
                        if (duration > 0) {
                            val progressFraction = position.toFloat() / duration
                            setSongProgress(progressFraction, position)
                        }
                    } catch (t: Throwable) {
                        // boundPlayer might be released — swallow safely
                    }
                    progressHandler.postDelayed(this, 500)
                }
            }
            progressHandler.post(progressRunnable!!)
        }

        private fun stopProgressHandler() {
            progressRunnable?.let {
                progressHandler.removeCallbacks(it)
                progressRunnable = null
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    mainViewModel.setPlayerStatus(playerStatus.BUFFERING)
                }
                Player.STATE_READY -> {
                    if (boundPlayer.playWhenReady) {
                        mainViewModel.setPlayerStatus(playerStatus.PLAYING)

                        val ui = mainViewModel.uiState.value
                        if (ui.allAudioData[ui.playingHash]?.duration == 0L) {
                            mainViewModel.updateDurationForSong(ui.playingHash, boundPlayer.duration)
                        }
                    } else {
                        mainViewModel.setPlayerStatus(playerStatus.PAUSE)
                    }
                }
                Player.STATE_ENDED -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)
                    AppViewModels.player.playerManager.nextSong(mainViewModel, searchViewModel)
                }
                Player.STATE_IDLE -> {
                    mainViewModel.setPlayerStatus(playerStatus.IDLE)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && boundPlayer.playbackState == Player.STATE_READY) {
                mainViewModel.setPlayerStatus(playerStatus.PLAYING)
            } else if (!playWhenReady && boundPlayer.playbackState == Player.STATE_READY) {
                mainViewModel.setPlayerStatus(playerStatus.PAUSE)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressHandler() else stopProgressHandler()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {

            trackEndingHandled = false
            addTrackToMediaItems?.cancel()

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {

                moveToNextPosInQueue(mainViewModel)
                val uiState = mainViewModel.uiState.value
                val newIndex = uiState.posInQueue

                if (newIndex < uiState.currentQueue.size) {
                    mainViewModel.setPosInQueue(newIndex)
                    mainViewModel.setPlayingHash(uiState.currentQueue[newIndex].hashKey)
                }

                fetchQueueStream(mainViewModel)

            }
        }

        private var addTrackToMediaItems: Job? = null
        override fun onEvents(playerParam: Player, events: Player.Events) {
            val duration = playerParam.duration
            val position = playerParam.currentPosition
            val remaining = duration - position

            if (!trackEndingHandled && remaining in 0..25_000) {
                if (!AppViewModels.player.playerManager.doesSongHaveNext(mainViewModel)) {
                    trackEndingHandled = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        searchViewModel.prepareRelatedTracks(
                            MyApplication.globalContext!!,
                            mainViewModel.uiState.value.playingHash,
                            mainViewModel
                        )
                    }
                }
                else
                {
                    trackEndingHandled = true

                    val uiState = mainViewModel.uiState.value

                    if (uiState.posInQueue + 1 >= uiState.currentQueue.size) return

                    val nextSongHash = uiState.currentQueue[uiState.posInQueue + 1].hashKey

                    if (nextSongHash.isNullOrEmpty())
                        return

                    val song = uiState.allAudioData[nextSongHash] ?: return

                    addTrackToMediaItems?.cancel()

                    addTrackToMediaItems = CoroutineScope(Dispatchers.Main).launch {
                        val mediaUri: Uri = if (song.file?.exists() == true) {
                            Uri.fromFile(song.file)
                        } else {
                            val streamUrl = mainViewModel.awaitStreamUrlFor(song.link)
                                ?: return@launch
                            Uri.parse(streamUrl)
                        }

                        val songArtResult = mainViewModel.awaitSongArt(mainViewModel, nextSongHash)

                        val mediaMetadataBuilder = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artists.joinToString { it.title })

                        when (songArtResult) {
                            is PlayerViewModel.SongArtResult.BitmapResult -> {
                                val smallBitmap = Bitmap.createScaledBitmap(songArtResult.bitmap, 256, 256, true)
                                val bytes = bitmapToCompressedBytes(smallBitmap, Bitmap.CompressFormat.JPEG, 85)
                                mediaMetadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                            is PlayerViewModel.SongArtResult.UrlResult -> {
                                mediaMetadataBuilder.setArtworkUri(Uri.parse(songArtResult.url))
                            }
                        }

                        val mediaItem = MediaItem.Builder()
                            .setUri(mediaUri)
                            .setTag(song.link)
                            .setMediaMetadata(mediaMetadataBuilder.build())
                            .build()


                        AppViewModels.player.playerManager.player.addMediaItem(mediaItem)

                        val currentIndex = AppViewModels.player.playerManager.player.currentMediaItemIndex
                        if (currentIndex > 0) {
                            AppViewModels.player.playerManager.player.removeMediaItems(0, currentIndex)
                        }


                    }

                }
            }

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                trackEndingHandled = false
                addTrackToMediaItems?.cancel()
            }
        }
    }

    boundPlayer.addListener(listener)

    attachedPlayer = boundPlayer
    attachedListener = listener

    snapshotJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        snapshotFlow { songPendingIntentNavigationDirection.value }
            .collect { direction ->
                when (direction) {
                    pendingDirection.TO_NEXT_SONG -> {
                        AppViewModels.player.playerManager.nextSong(mainViewModel, searchViewModel)
                        songPendingIntentNavigationDirection.value = pendingDirection.EMPTY
                    }
                    pendingDirection.TO_PREVIOUS_SONG -> {
                        AppViewModels.player.playerManager.prevSong(mainViewModel, searchViewModel)
                        songPendingIntentNavigationDirection.value = pendingDirection.EMPTY
                    }
                    pendingDirection.EMPTY -> {

                    }
                }
            }
    }

}