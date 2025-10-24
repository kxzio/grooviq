package com.example.groviq.backEnd.playEngine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Handler
import kotlin.math.min

private var attachedPlayer      : Player? = null
private var attachedListener    : Player.Listener? = null
private var snapshotJob         : kotlinx.coroutines.Job? = null
var addTrackToMediaItems: Job? = null

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

                        val currentMediaId = boundPlayer.currentMediaItem?.mediaId
                        val duration = boundPlayer.duration

                        if (currentMediaId != null && duration != C.TIME_UNSET) {
                            val song = mainViewModel.uiState.value.allAudioData[currentMediaId]
                            if (song != null && song.duration == 0L) {
                                mainViewModel.updateDurationForSong(currentMediaId, duration)
                            }
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
            mainViewModel.uiState.value.isPlaying = playWhenReady

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

                updateNextSongHash  (mainViewModel)

                fetchQueueStream(mainViewModel)

                //update image if needed
                val song = mainViewModel.uiState.value.allAudioData[uiState.currentQueue[newIndex].hashKey] ?: return

                CoroutineScope(Dispatchers.IO).launch {

                    val artLink = song.art_link
                    val fileUri = song.fileUri?.takeIf { song.localExists() }

                    if (fileUri == null)
                    {
                        if (artLink != null) {
                            val size = getImageSizeFromUrl(artLink)
                            val tooSmall = size == null || min(size.first, size.second) < 200
                            if (tooSmall) {
                                fetchNewImage(mainViewModel, song.link)
                            }
                        } else {
                            fetchNewImage(mainViewModel, song.link)
                        }
                    }
                }


            }

            val currentMediaId = boundPlayer.currentMediaItem?.mediaId
            val duration = boundPlayer.duration

            if (currentMediaId != null && duration != C.TIME_UNSET) {
                val song = mainViewModel.uiState.value.allAudioData[currentMediaId]
                if (song != null && song.duration == 0L) {
                    mainViewModel.updateDurationForSong(currentMediaId, duration)
                }
            }

            prepareAndAddNextTrackToMediaItems(mainViewModel)
        }


        override fun onEvents(playerParam: Player, events: Player.Events) {
            val duration = playerParam.duration
            val position = playerParam.currentPosition
            val remaining = duration - position

            if (!trackEndingHandled && remaining in 0..25_000) {
                if (!AppViewModels.player.playerManager.doesSongHaveNext(mainViewModel)) {
                    trackEndingHandled = true

                    if (mainViewModel.uiState.value.allAudioData[mainViewModel.uiState.value.playingHash]?.isExternal == true)
                        return

                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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

    snapshotJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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

@OptIn(UnstableApi::class)
fun prepareAndAddNextTrackToMediaItems(mainViewModel: PlayerViewModel) {


    val uiState = mainViewModel.uiState.value
    if (uiState.shouldRebuild) return

    addTrackToMediaItems?.cancel()

    if (uiState.repeatMode == repeatMods.REPEAT_ONE)
        return

    addTrackToMediaItems = CoroutineScope(Dispatchers.IO).launch {
        val nextIndex = uiState.posInQueue + 1
        val nextSong = uiState.currentQueue.getOrNull(nextIndex)
            ?.let { uiState.allAudioData[it.hashKey] } ?: return@launch

        val player = AppViewModels.player.playerManager.player

        val alreadyAdded = withContext(Dispatchers.Main) {
            (0 until player.mediaItemCount)
                .any { player.getMediaItemAt(it).mediaId == nextSong.link }
        }

        if (alreadyAdded) {
            withContext(Dispatchers.Main) {
                if (!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                    player.prepare()
                }
            }
            return@launch
        }

        val mediaUri: Uri? = nextSong.fileUri
            ?.takeIf { nextSong.localExists() }
            ?.let { Uri.parse(it) }
            ?: mainViewModel.awaitStreamUrlFor(nextSong.link)?.let { Uri.parse(it) }

        if (mediaUri == null) return@launch

        val songArtResult = if (
            mainViewModel.uiState.value.allAudioData[nextSong.link]?.art_link.isNullOrEmpty() == true &&
            mainViewModel.uiState.value.allAudioData[nextSong.link]?.art_local_link.isNullOrEmpty()
        ) {
            null
        } else {
            mainViewModel.awaitSongArt(mainViewModel, nextSong.link)
        }

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(nextSong.title)
            .setArtist(nextSong.artists.joinToString { it.title })

        when (songArtResult) {
            is PlayerViewModel.SongArtResult.BitmapResult -> {
                val smallBitmap = Bitmap.createScaledBitmap(songArtResult.bitmap, 256, 256, true)
                val bytes = bitmapToCompressedBytes(smallBitmap, Bitmap.CompressFormat.JPEG, 85)
                mediaMetadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            is PlayerViewModel.SongArtResult.UrlResult -> {
                mediaMetadataBuilder.setArtworkUri(Uri.parse(songArtResult.url))
            }
            null -> Unit
        }

        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaId(nextSong.link)
            .setTag(nextSong.link)
            .setMediaMetadata(mediaMetadataBuilder.build())
            .build()

        withContext(Dispatchers.Main) {
            player.addMediaItem(mediaItem)
            player.prepare()
        }
    }
}