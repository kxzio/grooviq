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
import com.example.groviq.backEnd.streamProcessor.fetchNewImage
import com.example.groviq.backEnd.streamProcessor.fetchQueueStream
import com.example.groviq.bitmapToCompressedBytes
import com.example.groviq.getImageSizeFromUrl
import com.example.groviq.service.nextSongHashPending
import com.example.groviq.service.pendingDirection
import com.example.groviq.service.songPendingIntentNavigationDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Handler
import kotlin.math.min

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
            val uiState = mainViewModel.uiState.value
            addTrackToMediaItems?.cancel()

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                moveToNextPosInQueue(mainViewModel)
                val newIndex = mainViewModel.uiState.value.posInQueue
                if (newIndex < uiState.currentQueue.size) {
                    mainViewModel.setPosInQueue(newIndex)
                    mainViewModel.setPlayingHash(uiState.currentQueue[newIndex].hashKey)
                }
                fetchQueueStream(mainViewModel)
            }

            val nextIndex = uiState.posInQueue + 1
            val nextSong = uiState.currentQueue.getOrNull(nextIndex)?.let { uiState.allAudioData[it.hashKey] }
                ?: return

            addTrackToMediaItems = CoroutineScope(Dispatchers.Main).launch {
                val player = AppViewModels.player.playerManager.player
                val nextIndex = uiState.posInQueue + 1
                val nextSong = uiState.currentQueue.getOrNull(nextIndex)?.let { uiState.allAudioData[it.hashKey] } ?: return@launch

                val alreadyAdded = (0 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it) }
                    .any { it.mediaId == nextSong.link }

                if (alreadyAdded) return@launch

                val mediaUri: Uri = nextSong.file?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
                    ?: mainViewModel.awaitStreamUrlFor(nextSong.link)?.let { Uri.parse(it) }
                    ?: return@launch

                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(nextSong.title)
                    .setArtist(nextSong.artists.joinToString { it.title })

                val songArtResult = mainViewModel.awaitSongArt(mainViewModel, nextSong.link)
                when (songArtResult) {
                    is PlayerViewModel.SongArtResult.BitmapResult -> {
                        val smallBitmap = Bitmap.createScaledBitmap(songArtResult.bitmap, 256, 256, true)
                        val bytes = bitmapToCompressedBytes(smallBitmap, Bitmap.CompressFormat.JPEG, 85)
                        metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    is PlayerViewModel.SongArtResult.UrlResult -> {
                        metadataBuilder.setArtworkUri(Uri.parse(songArtResult.url))
                    }
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setTag(nextSong.link)
                    .setMediaId(nextSong.link)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()

                player.addMediaItem(mediaItem)
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
                }
            }

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                trackEndingHandled = false
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