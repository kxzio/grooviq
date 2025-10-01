package com.example.groviq.backEnd.playEngine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.bumptech.glide.Glide
import com.example.groviq.AppViewModels
import com.example.groviq.MainActivity
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.currentRelatedTracksJob
import com.example.groviq.backEnd.searchEngine.lastRecommendListProcessed
import com.example.groviq.backEnd.searchEngine.preparedRecommendList
import com.example.groviq.backEnd.streamProcessor.cancelFetchForSong
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
import com.example.groviq.backEnd.streamProcessor.fetchNewImage
import com.example.groviq.backEnd.streamProcessor.fetchQueueStream
import com.example.groviq.bitmapToCompressedBytes
import com.example.groviq.getImageSizeFromUrl
import com.example.groviq.isServiceRunning
import com.example.groviq.isSmall
import com.example.groviq.service.CustomPlayer
import com.example.groviq.service.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min


@UnstableApi
class AudioPlayerManager(context: Context) {

    //main player
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2000,
            /* maxBufferMs = */ 50000,
            /* bufferForPlaybackMs = */ 500,
            /* bufferForPlaybackAfterRebufferMs = */ 1500
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val cacheDir = File(context.cacheDir, "media")
    val cacheEvictor = LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L)
    val databaseProvider = ExoDatabaseProvider(context)

    val simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)

    val upstreamFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(10_000)
        .setKeepPostFor302Redirects(true)
        .setTransferListener(DefaultBandwidthMeter.getSingletonInstance(context))

    val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(simpleCache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val defaultDataSourceFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)


    val notOverridedPlayer: ExoPlayer = ExoPlayer.Builder(MyApplication.globalContext!!)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(mediaSourceFactory)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build().apply {
            setHandleAudioBecomingNoisy(true)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
        }

    var player = CustomPlayer(notOverridedPlayer)

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

    @OptIn(
        UnstableApi::class
    )

    fun playAtIndex(mainViewModel: PlayerViewModel, searchViewModel: SearchViewModel, targetIndex: Int) {
        
        val view = mainViewModel.uiState.value

        val currentQueue = view.currentQueue
        if (currentQueue.isNullOrEmpty()) return

        val idx = targetIndex.coerceIn(0, currentQueue.size - 1)

        currentRelatedTracksJob?.cancel()

        mainViewModel.setPosInQueue(idx)

        AppViewModels.player.playerManager.play(currentQueue[idx].hashKey, mainViewModel, searchViewModel)
    }


    fun play(hashkey : String, mainViewModel: PlayerViewModel, searchViewModel: SearchViewModel, userPressed : Boolean = false) {

        //check bounding box
        if (mainViewModel.uiState.value.allAudioData[hashkey] == null)
            return

        trackEndingHandled = false

        createListeners(searchViewModel, mainViewModel)

        //check cooldown
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimeMs < PLAY_COOLDOWN_MS) return
        lastPlayTimeMs = now

        if (player != null)
            player!!.stop()

        //if we start playing another track, but last song didnt get the stream yet, we should cancel the thread to not overload
        if (hashkey != mainViewModel.uiState.value.playingHash) {

            //cancel all threads

            val oldKey = mainViewModel.uiState.value.playingHash
            if (oldKey != null) {
                cancelFetchForSong(oldKey, mainViewModel)
            }

            currentPlaybackJob      ?.cancel()
            currentRelatedTracksJob ?.cancel()
            mainViewModel.setSongsLoadingStatus(false)

        }

        //current playing index in hash value
        mainViewModel.setPlayingHash(hashkey)

        setSongProgress(0f, 0L)

        //erase the list, that we prepared for song
        preparedRecommendList       = mutableListOf()
        if (userPressed)
        {
            //no song to recommend
            lastRecommendListProcessed  = mutableListOf()
        }

        if (mainViewModel.uiState.value.shouldRebuild && userPressed)
        {
            //reset all
            mainViewModel        .setQueue(mutableListOf())
            mainViewModel.setOriginalQueue(emptyList())

            //if queue was changed and user pressed, we have to recover original queue
            createQueueOnAudioSourceHash(mainViewModel, hashkey)
            mainViewModel.setShouldRebuild(false)
        }
        else if (mainViewModel.uiState.value.lastSourceBuilded != mainViewModel.uiState.value.playingAudioSourceHash ||
            (userPressed && mainViewModel.uiState.value.isShuffle))
        {
            mainViewModel.uiState.value.currentQueue    = mutableListOf()
            mainViewModel.uiState.value.originalQueue   = emptyList()

            //if the audio source changed, we have to rebuild the queue
            createQueueOnAudioSourceHash(mainViewModel, hashkey)
            //update last built audio source
            mainViewModel.setLastSourceBuilded(mainViewModel.uiState.value.playingAudioSourceHash)
        }


        //clear all songs that we had by surfing the web, now we have to delete them, because they have no clue, since user played song
        mainViewModel.clearUnusedAudioSourcedAndSongs(searchViewModel)


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


            //reactive waiting for stream url

            if (fileUri == null)
            {
                val streamUrl = mainViewModel.awaitStreamUrlFor(hashkey)

                if (streamUrl != null) {
                    //back to UI layer
                    withContext(Dispatchers.Main)
                    {

                        val songArtResult = mainViewModel.awaitSongArt(mainViewModel, hashkey)

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

                        val mediaUri: Uri = fileUri?.let {
                            Uri.parse(fileUri)
                        } ?: Uri.parse(streamUrl)

                        val mediaItem = MediaItem.Builder()
                            .setUri(mediaUri)
                            .setMediaId(song.link)
                            .setTag(song.link)
                            .setMediaMetadata(mediaMetadataBuilder.build())
                            .build()

                        updateNextSongHash(mainViewModel)

                        player!!.setMediaItem(mediaItem)
                        player!!.prepare()
                        player!!.playWhenReady = true
                        player!!.repeatMode = Player.REPEAT_MODE_OFF
                        player!!.shuffleModeEnabled = false

                        val svcIntent = Intent(MyApplication.globalContext, PlayerService::class.java)
                        if (!isServiceRunning(MyApplication.globalContext!!, PlayerService::class.java)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(MyApplication.globalContext, svcIntent)
                            } else {
                                MyApplication.globalContext!!.startService(svcIntent)
                            }
                        }

                        //save updated stream
                        mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashkey]!!)

                        fetchQueueStream(mainViewModel)


                    }
                }
            }
            else
            {
                if (true) {
                    //back to UI layer
                    withContext(Dispatchers.Main)
                    {

                        val songArtResult = mainViewModel.awaitSongArt(mainViewModel, hashkey)

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

                        val mediaUri: Uri? = fileUri?.let { Uri.parse(it) }

                        val mediaItem = MediaItem.Builder()
                            .setUri(mediaUri)
                            .setMediaId(song.link)
                            .setTag(song.link)
                            .setMediaMetadata(mediaMetadataBuilder.build())
                            .build()

                        player!!.setMediaItem(mediaItem)
                        player!!.prepare()
                        player!!.playWhenReady = true
                        player!!.repeatMode = Player.REPEAT_MODE_OFF
                        player!!.shuffleModeEnabled = false

                        val svcIntent = Intent(MyApplication.globalContext, PlayerService::class.java)
                        if (!isServiceRunning(MyApplication.globalContext!!, PlayerService::class.java)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(MyApplication.globalContext, svcIntent)
                            } else {
                                MyApplication.globalContext!!.startService(svcIntent)
                            }
                        }

                        //save updated stream
                        mainViewModel.saveSongToRoom(mainViewModel.uiState.value.allAudioData[hashkey]!!)

                        updateNextSongHash(mainViewModel)

                        fetchQueueStream(mainViewModel)


                    }
                }
            }



        }

    }

    fun pause() {
        player!!.pause()
    }

    fun resume() {
        player!!.play()
    }

    fun stop() {
        player!!.stop()
    }

    fun release() {
        player!!.release()
    }

    fun doesSongHaveNext(mainViewModel: PlayerViewModel) : Boolean
    {
        val view = mainViewModel.uiState.value
        val repeatMode = view.repeatMode
        val isShuffle = view.isShuffle
        val currentQueue = view.currentQueue

        if (currentQueue.isNullOrEmpty())
            return false

        val originalQueue = view.originalQueue
        val pos = view.posInQueue

        if (repeatMode == repeatMods.REPEAT_ONE) {
            return true
        }

        val nextIndex = pos + 1

        if (nextIndex < currentQueue.size) {
            return true
        }

        // reached end of queue
        if (repeatMode == repeatMods.REPEAT_ALL) {
            return true
        }

        return false

    }

    fun nextSong(mainViewModel: PlayerViewModel, searchViewModel: SearchViewModel) {

        val view = mainViewModel.uiState.value

        val repeatMode = view.repeatMode
        val isShuffle = view.isShuffle
        val currentQueue = view.currentQueue

        if (currentQueue.isNullOrEmpty())
            return

        val originalQueue = view.originalQueue
        val pos = view.posInQueue

        if (repeatMode == repeatMods.REPEAT_ONE) {
            // Повтор одного трека
            AppViewModels.player.playerManager.play(currentQueue[pos].hashKey, mainViewModel, searchViewModel )
            return
        }

        val nextIndex = pos + 1

        if (nextIndex < currentQueue.size) {
            moveToNextPosInQueue(mainViewModel)
            val newPos = mainViewModel.uiState.value.posInQueue
            AppViewModels.player.playerManager.play(currentQueue[newPos].hashKey, mainViewModel, searchViewModel)
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
            AppViewModels.player.playerManager.play(newQueue[0].hashKey, mainViewModel, searchViewModel)
            return
        }

        player.stop()

        if (mainViewModel.uiState.value.songsLoader == true)
            return

        currentRelatedTracksJob?.cancel()

        currentRelatedTracksJob = CoroutineScope(Dispatchers.Main).launch {
            // NO REPEAT

            mainViewModel.setSongsLoadingStatus(true)

            val audioSourcePath = searchViewModel.addRelatedTracksToCurrentQueue(
                MyApplication.globalContext!!,
                currentQueue[pos].hashKey,
                mainViewModel
            )

            mainViewModel.waitAudioSoureToAppearAndPlayNext(searchViewModel, audioSourcePath)
        }
    }

    fun prevSong(mainViewModel: PlayerViewModel, searchViewModel: SearchViewModel)
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

        play(viewState.currentQueue[viewState.posInQueue].hashKey, mainViewModel, searchViewModel)
    }



}
