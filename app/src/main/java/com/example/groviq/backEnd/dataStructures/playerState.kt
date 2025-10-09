package com.example.groviq.backEnd.dataStructures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.localFileProcessor.extractMetadataFromFile
import com.example.groviq.backEnd.localFileProcessor.findAudioInFolderRecursively
import com.example.groviq.backEnd.playEngine.AudioPlayerManager
import com.example.groviq.backEnd.playEngine.onShuffleToogle
import com.example.groviq.backEnd.playEngine.queueElement
import com.example.groviq.backEnd.playEngine.updateNextSongHash
import com.example.groviq.backEnd.playEngine.updatePosInQueue
import com.example.groviq.backEnd.saveSystem.DataRepository
import com.example.groviq.backEnd.saveSystem.FolderEntity
import com.example.groviq.backEnd.searchEngine.ArtistDto
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.streamProcessor.DownloadManager
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.getArtFromURI
import com.example.groviq.hasInternetConnection
import com.example.groviq.loadBitmapFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

enum class playerStatus {
    IDLE,
    PLAYING,
    PAUSE,
    LOADING_AUDIO,
    BUFFERING
}

enum class repeatMods {
    NO_REPEAT,
    REPEAT_ONE,
    REPEAT_ALL
}

data class playerState(

    //current state of audio player
    var currentStatus : playerStatus    = playerStatus.IDLE,
    var songsLoader   : Boolean         = false,

    //all songs in system
    var allAudioData : MutableMap<String, songData> = mutableMapOf(),

    //keys of songs in albums, playlists. for example. use key for album (link of album) or use name of playlist as key
    var audioData     : MutableMap<String, audioSource> = mutableMapOf(),

    //list of folders that user added
    var localFilesFolders : MutableList<ViewFolder> = mutableListOf(),

    //curent played data
    var playingHash              : String = "", // to indicate, whitch audio playing right now
    var playingAudioSourceHash   : String = "", // to indicate, whitch audio source is playing right now
    var searchBroserFocus        : String = "", // to not delete the data we currently see on the browser screen

    //queue info
    var currentQueue    : MutableList   < queueElement > = mutableListOf(), // the queue we have in current
    var originalQueue   : List          < queueElement > = emptyList(),     // the queue we had before the shuffle
    var shouldRebuild   : Boolean = false,                                  // indicate if we have to rebuild the queue from zero

    var posInQueue  : Int = -1,
    var lastSourceBuilded : String = "",

    //player mods
    var isShuffle   : Boolean = false,
    var repeatMode  : repeatMods = repeatMods.NO_REPEAT,

    var isPlaying: Boolean = false

    //saved audiosources

    )


class PlayerViewModelFactory(private val repository: DataRepository) : ViewModelProvider.Factory {
    @OptIn(
        UnstableApi::class
    )
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PlayerViewModel(repository) as T
    }
}


@UnstableApi
class PlayerViewModel(private val repository: DataRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(playerState())
    val uiState: StateFlow<playerState> = _uiState

    //mutex control = MEMORY RACE CONTROL
    private val stateLock = ReentrantLock()
    private fun <T> withState(block: (playerState) -> T): T =
        stateLock.withLock { block(_uiState.value) }

    fun updateState(block: (playerState) -> playerState) {
        stateLock.withLock {
            _uiState.value = block(_uiState.value)
        }
    }

    val playerManager = AudioPlayerManager(MyApplication.globalContext)

    fun loadAllFromRoom() {

        viewModelScope.launch {
            val (songsMap, audioMap, folders) = withContext(Dispatchers.IO) {
                repository.loadAllAudioSources(MyApplication.globalContext!!)
            }
            updateState {
                it.copy(
                    allAudioData         = songsMap.toMutableMap(),
                    audioData            = audioMap.toMutableMap(),
                    localFilesFolders    = folders.toMutableList()
                )
            }
            clearRoomBd()
        }

    }

    fun saveAllFolders() {
        val foldersToSave = withState { it.localFilesFolders.toList() }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveFolders(foldersToSave)
        }
    }

    fun saveSongToRoom(song: songData) {
        viewModelScope.launch(Dispatchers.IO) { repository.saveSong(this@PlayerViewModel, song, MyApplication.globalContext!!) }
    }

    fun clearRoomBd() {
        viewModelScope.launch(Dispatchers.IO) {
            val allSongs = uiState.value.allAudioData.values.toList()
            val allAudioTrackIds = uiState.value.audioData.values
                .flatMap { it.songIds }
                .toSet()

            allSongs.forEach { song ->
                if (song.fileUri !in allAudioTrackIds) {
                    repository.saveSong(this@PlayerViewModel, song, MyApplication.globalContext!!)
                }
            }
        }
    }

    fun saveSongsFromSourceToRoom(string: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = withState { state ->
                state.audioData[string]?.songIds
                    ?.mapNotNull { state.allAudioData[it] }
                    ?: emptyList()
            }
            songs.forEach { song ->
                repository.saveSong(this@PlayerViewModel, song, MyApplication.globalContext!!)
            }
        }
    }

    fun saveAudioSourcesToRoom() {
        viewModelScope.launch(Dispatchers.IO) { repository.saveAudioSources(this@PlayerViewModel) }
    }

    //current status of player : BUFFERING, IDLE and etc. to prevent user from not good UI decisions
    fun setPlayerStatus(status: playerStatus) {
        updateState { it.copy(currentStatus = status) }
    }

    fun setSongsLoadingStatus(status: Boolean) {
        updateState { it.copy(songsLoader = status) }
    }

    fun setPlayingHash(songLink: String) {
        updateState { it.copy(playingHash = songLink) }
    }

    fun setPlayingAudioSourceHash(audioSourceLink: String) {
        updateState { it.copy(playingAudioSourceHash = audioSourceLink) }
    }

    fun deleteUserAdds() {
        updateState {
            it.copy(
                currentQueue = it.currentQueue.filter { q -> !q.addedByUser }.toMutableList(),
                originalQueue = it.originalQueue.filter { q -> !q.addedByUser }.toMutableList()
            )
        }
    }

    fun setQueue(newQueue: MutableList<queueElement>) {
        updateState { it.copy(currentQueue = newQueue) }
    }

    fun setOriginalQueue(newQueue: List<queueElement>) {
        updateState { it.copy(originalQueue = newQueue) }
    }

    fun setShouldRebuild(b: Boolean) {
        updateState { it.copy(shouldRebuild = b) }
        updateNextSongHash(this)
    }

    fun setPosInQueue(newPos: Int) {
        updateState { it.copy(posInQueue = newPos) }
    }

    fun setLastSourceBuilded(newSource: String) {
        updateState { it.copy(lastSourceBuilded = newSource) }
    }

    fun toogleShuffleMode() {
        updateState { state ->

            val newShuffle =
                !state.isShuffle

            val newState =
                state.copy(
                    isShuffle = newShuffle
                )

            newState
        }
        onShuffleToogle(mainViewModel = this, uiState.value.isShuffle)
    }

    fun toogleRepeatMode() {
        updateState { state ->
            state.copy(
                repeatMode =
                if (state.repeatMode == repeatMods.NO_REPEAT) repeatMods.REPEAT_ALL
                else if (state.repeatMode == repeatMods.REPEAT_ALL) repeatMods.REPEAT_ONE
                else repeatMods.NO_REPEAT
            )
        }
        updateNextSongHash(this)
    }

    fun setAlbumTracks(
        request: String,
        tracks: List<songData>,
        audioSourceName: String,
        audioSourceArtist: List<ArtistDto>,
        audioSourceYear: String,
        //autogenerated info
        autoGeneratedB: Boolean = false,
        isExternalSource : Boolean    = false //audio from local files
    ) {
        updateState { currentUiState ->
            val updatedAllAudio = currentUiState.allAudioData.toMutableMap()
            for (track in tracks) {
                if (!updatedAllAudio.containsKey(track.link)) {
                    updatedAllAudio[track.link] = track
                }
            }

            val updatedAudioData = currentUiState.audioData.toMutableMap()
            updatedAudioData[request] = audioSource().apply {
                songIds = tracks.map { it.link }.toMutableList()
                nameOfAudioSource = audioSourceName
                artistsOfAudioSource = audioSourceArtist
                yearOfAudioSource = audioSourceYear
                //generated info
                autoGenerated = autoGeneratedB
                shouldBeSavedStrictly = isExternalSource
                isExternal = isExternalSource
                timeUpdate = if (autoGeneratedB) {
                    System.currentTimeMillis()
                } else {
                    // для обычных всегда 0
                    0L
                }
            }

            currentUiState.copy(
                allAudioData = updatedAllAudio,
                audioData = updatedAudioData
            )
        }
    }

    fun markSourceGenerationInProgress(key: String, inProgress: Boolean) {
        updateState { state ->
            val map = state.audioData.toMutableMap()
            val src = map[key]
            if (src != null) {
                map[key] = src.copy(isInGenerationProcess = inProgress)
            }
            state.copy(audioData = map)
        }
    }

    fun deleteAudioSource(
        targetAudioSource: String,
    ) {
        updateState { currentUiState ->

            val updatedAudioData = currentUiState.audioData.toMutableMap()

            updatedAudioData.remove(targetAudioSource)

            if (currentUiState.playingAudioSourceHash == targetAudioSource) {
                setShouldRebuild(true)
                setLastSourceBuilded("")
                updateNextSongHash(this)
            }

            currentUiState.copy(
                audioData = updatedAudioData
            )
        }
    }

    fun renameAudioSourceAndMoveSongs(
        targetAudioSource: String,
        newNameOfAudioSource: String
    ) {
        val oldNameOfAudioSource = targetAudioSource

        updateState { currentUiState ->

            val updatedAudioData = currentUiState.audioData.toMutableMap()

            val oldSource = updatedAudioData[targetAudioSource] ?: return@updateState currentUiState
            updatedAudioData.remove(targetAudioSource)

            val newSource = oldSource.copy(nameOfAudioSource = newNameOfAudioSource)
            updatedAudioData[newNameOfAudioSource] = newSource

            if (currentUiState.playingAudioSourceHash == oldNameOfAudioSource) {
                setShouldRebuild(true)
                setLastSourceBuilded("")
                updateNextSongHash(this)
            }

            currentUiState.copy(
                audioData = updatedAudioData
            )
        }
    }


    fun toggleStrictSaveAudioSource(request: String) {
        updateState { currentState ->
            val updatedAudioData = currentState.audioData.toMutableMap()
            val oldValue = updatedAudioData[request]?.shouldBeSavedStrictly
            if (oldValue != null) {
                val updatedItem = updatedAudioData[request]!!.copy(
                    shouldBeSavedStrictly = !oldValue
                )
                updatedAudioData[request] = updatedItem
            }
            currentState.copy(audioData = updatedAudioData)
        }
    }

    fun setTrack(
        request: String,
        song: songData,
    ) {
        updateState { currentUiState ->
            val updatedAllAudio = currentUiState.allAudioData.toMutableMap()
            if (!updatedAllAudio.containsKey(request)) {
                updatedAllAudio[request] = song
            }

            val updatedAudioData = currentUiState.audioData.toMutableMap()
            updatedAudioData[request] = audioSource().apply {
                songIds = listOf(song.link).toMutableList()
                nameOfAudioSource = request
            }

            currentUiState.copy(
                allAudioData = updatedAllAudio,
                audioData = updatedAudioData
            )
        }
    }

    fun updateStreamForSong(songLink: String, streamUrl: String) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] =
                    song.copy(stream = streamInfo(streamUrl, if (streamUrl == "") 0L else System.currentTimeMillis(), isVideo = song.stream.isVideo))
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateStreamForSongByPair(songLink: String, streamUrl: Pair<String?, Boolean>) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] =
                    song.copy(
                        stream = streamInfo(streamUrl.first ?: "", if (streamUrl.first == "") 0L else System.currentTimeMillis(), isVideo = streamUrl.second)
                    )
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateDurationForSong(songLink: String, duration: Long) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] = song.copy(duration = duration)
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun addSongToAudioSource(songLink: String, audioSource: String) {
        updateState { currentState ->
            val song = currentState.allAudioData[songLink] ?: return@updateState currentState
            val updatedAudioData = currentState.audioData.toMutableMap()
            val currentEntry = updatedAudioData[audioSource]
            val currentIds = currentEntry?.songIds ?: emptyList()
            val newIds = currentIds.toMutableList().apply {
                if (!contains(songLink)) add(0, songLink)
            }
            updatedAudioData[audioSource] = audioSource(songIds = newIds)
            currentState.copy(audioData = updatedAudioData)
        }
    }

    fun removeSongFromAudioSource(songLink: String, audioSource: String) {
        updateState { currentState ->
            val song = currentState.allAudioData[songLink] ?: return@updateState currentState
            val updatedAudioData = currentState.audioData.toMutableMap()
            val currentEntry = updatedAudioData[audioSource] ?: return@updateState currentState
            val currentIds = currentEntry.songIds
            if (!currentIds.contains(songLink)) return@updateState currentState
            val newIds = currentIds.toMutableList().apply { remove(songLink) }
            updatedAudioData[audioSource] = audioSource(songIds = newIds)
            currentState.copy(audioData = updatedAudioData)
        }
    }

    fun isAudioSourceContainsSong(songLink: String, audioSource: String): Flow<Boolean> {
        return uiState.map { state ->
            val audioSourceEntry = state.audioData[audioSource]
            audioSourceEntry?.songIds?.contains(songLink) == true
        }
    }

    fun isAudioSourceAlreadyHad(audioSource: String): Boolean {
        return uiState.value.audioData[audioSource] != null
    }


    fun updateStatusForSong(songLink: String, status: songProgressStatus) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] = song.copy(progressStatus = status)
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateFileForSong(songLink: String, file: File?) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                val newUri = file?.let { Uri.fromFile(it).toString() }
                updatedAllAudioData[songLink] = song.copy(
                    fileUri = newUri
                )
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateImageForSong(songLink: String, link: String) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] = song.copy(art_link = link)
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    fun updateDownloadingProgressForSong(songLink: String, progress: Float) {
        updateState { currentState ->
            val updatedAllAudioData = currentState.allAudioData.toMutableMap()
            val song = updatedAllAudioData[songLink]
            if (song != null) {
                updatedAllAudioData[songLink] =
                    song.copy(progressStatus = song.progressStatus.copy(downloadingProgress = progress))
            }
            currentState.copy(allAudioData = updatedAllAudioData)
        }
    }

    suspend fun awaitStreamUrlFor(hash: String, timeoutMs: Long = 60_000L): String? =
        withTimeoutOrNull(timeoutMs) {
            uiState
                .map { state -> state.allAudioData[hash] }
                .filterNotNull()
                .firstOrNull { song ->
                    song.stream.streamUrl.isNotEmpty() && !song.shouldGetStream()
                }
                ?.stream
                ?.streamUrl
        }

    fun clearUnusedAudioSourcedAndSongs(searchViewModel: SearchViewModel) {

        val currentPlayingAudioSource = withState { it.playingAudioSourceHash }
        val playlists = withState { state -> state.audioData.filter { !it.key.contains("https://") } }
        val currentQueueAudioSources = withState { it.currentQueue.map { q -> q.audioSource }.toSet() }

        updateState { state ->

            val filteredAudioData = state.audioData.filter {

                state.audioData[it.key]?.shouldBeSavedStrictly ?: false ||           //this audiosource is saved strictly

                it.key == currentPlayingAudioSource ||                              //we play this audioSource

                playlists.containsKey(it.key) ||                                    //this audioSource is playlist

                state.audioData[it.key]?.songIds?.any { songId ->                   //if download queue has song from this audio-source
                            songId in DownloadManager.state.value.active ||
                                    songId in DownloadManager.state.value.queued
                } ?: false ||

                 it.key == state.searchBroserFocus ||                               //this audiosource is UI focused

                 currentQueueAudioSources.contains(it.key) ||                       //this audiosource is used for queue building

                 it.key == searchViewModel.uiState.value.currentArtist.url ||       //current opened artist popular songs

                 state.audioData[it.key]?.isExternal == true

            }.toMutableMap()

            val usedSongIds = filteredAudioData.values
                .flatMap { it.songIds }
                .toSet()

            state.copy(
                audioData = filteredAudioData,
                allAudioData = state.allAudioData.filter { usedSongIds.contains(it.key) }.toMutableMap()
            )
        }
    }

    fun createAudioSource(name: String) {
        updateState { currentState ->
            val updatedAudioData = currentState.audioData.toMutableMap()
            updatedAudioData[name] = audioSource().apply {
                nameOfAudioSource = name
            }
            currentState.copy(audioData = updatedAudioData)
        }
    }

    fun isSongSavable(song: songData): Boolean {

        val playlists       = getPlaylists()

        val inPlaylist      = playlists.any { it.value.songIds.contains(song.link) }

        val inStrictSource  = withState { state ->

            state.audioData.any { (_, src) -> src.shouldBeSavedStrictly && song.link in src.songIds }

        }

        return inPlaylist || inStrictSource

    }

    fun getSavableAudioSources(): Map<String, audioSource> {
        val playlists = getPlaylists()
        val strictSaved = withState { it.audioData.filter { entry -> entry.value.shouldBeSavedStrictly } }
        return playlists + strictSaved
    }

    fun isPlaylist(audioSource: String): Boolean {

        val isPlaylist =

        audioSource.contains("https://").not() && (uiState.value.audioData[audioSource]?.isExternal == false)

        return isPlaylist
    }

    fun getPlaylists(): Map<String, audioSource> {
        return withState { state -> state.audioData.filter { isPlaylist(it.key) } }
    }

    fun updateBrowserHashFocus(hash: String) {
        updateState { it.copy(searchBroserFocus = hash) }
    }

    fun waitTrackAndPlay(searchViewModel: SearchViewModel, hash: String, audioSourcePath: String) {
        if (AppViewModels.player.playerManager.player != null)
            AppViewModels.player.playerManager.player!!.stop()

        updateState { it.copy(playingHash = "", playingAudioSourceHash = "") }

        viewModelScope.launch {
            val track = uiState
                .map { state -> state.allAudioData[hash] }
                .filterNotNull()
                .first()

            setPlayingAudioSourceHash(audioSourcePath)
            updatePosInQueue(this@PlayerViewModel, track.link)
            deleteUserAdds()
            AppViewModels.player.playerManager.play(track.link, this@PlayerViewModel, searchViewModel, true)
        }
    }

    fun waitAudioSoureToAppearAndPlayNext(searchViewModel: SearchViewModel, audioSourcePath: String) {
        viewModelScope.launch {
            uiState
                .map { state -> state.audioData[audioSourcePath] }
                .filterNotNull()
                .first()

            AppViewModels.player.playerManager.nextSong(mainViewModel = this@PlayerViewModel, searchViewModel)
        }
    }

    private val artListeners = mutableListOf<(String, Bitmap?) -> Unit>()

    fun addArtListener(listener: (String, Bitmap?) -> Unit) {
        artListeners.add(listener)
    }

    fun removeArtListener(listener: (String, Bitmap?) -> Unit) {
        artListeners.remove(listener)
    }

    sealed class SongArtResult {
        data class BitmapResult(val bitmap: Bitmap) : SongArtResult()
        data class UrlResult(val url: String) : SongArtResult()
    }

    suspend fun awaitSongArt(mainViewModel: PlayerViewModel, songKey: String): SongArtResult {

        val song = mainViewModel.uiState.value.allAudioData[songKey]
            ?: throw Exception("Song not found")

        song.art_local_link?.let { path ->
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) {
                return SongArtResult.BitmapResult(bmp)
            }
        }


        song.art_link?.let { artLink ->
            if (hasInternetConnection(MyApplication.globalContext)) {
                val modifiedLink = if (artLink.contains("=w")) {
                    artLink.replace(Regex("=w\\d+"), "=w200-h200")
                } else {
                    artLink
                }
                val bitmap = withContext(Dispatchers.IO) { loadBitmapFromUrl(modifiedLink) }
                bitmap?.let {
                    val scaled = Bitmap.createScaledBitmap(it, 200, 200, true)
                    return SongArtResult.BitmapResult(scaled)
                }
            }
        }

        return suspendCancellableCoroutine { cont ->
            val listener = object : (String, Bitmap?) -> Unit {
                override fun invoke(key: String, bitmap: Bitmap?) {
                    if (key == songKey && bitmap != null) {
                        mainViewModel.removeArtListener(this)
                        val scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                        cont.resume(SongArtResult.BitmapResult(scaled)) {}
                    }
                }
            }
            mainViewModel.addArtListener(listener)
            cont.invokeOnCancellation { mainViewModel.removeArtListener(listener) }
        }
    }

    val songFromFolderGenerator = mutableMapOf<String, Job>()

    fun generateSongsFromFolder(viewFolder: ViewFolder) {

        fun parseTrackNumber(num: String?): Int? {
            if (num.isNullOrBlank()) return null
            return try {
                num.substringBefore("/").toIntOrNull()
            } catch (e: Exception) {
                null
            }
        }

        songFromFolderGenerator[viewFolder.uri] = viewModelScope.launch(Dispatchers.IO) {

            try {
                val context = MyApplication.globalContext ?: return@launch

                val files = findAudioInFolderRecursively(Uri.parse(viewFolder.uri))
                if (files.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateState { state ->
                            state.copy(
                                localFilesFolders = state.localFilesFolders.map {
                                    if (it.uri == viewFolder.uri) it.copy(progressOfLoading = 1f)
                                    else it
                                }.toMutableList()
                            )
                        }
                    }
                    return@launch
                }

                val existingLinks = uiState.value.allAudioData
                    .mapNotNull { it.value.fileUri }
                    .toSet()

                val newFiles = files.filter { it.toString() !in existingLinks }
                if (newFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateState { state ->
                            state.copy(
                                localFilesFolders = state.localFilesFolders.map {
                                    if (it.uri == viewFolder.uri) it.copy(progressOfLoading = 1f)
                                    else it
                                }.toMutableList()
                            )
                        }
                    }
                    return@launch
                }

                val total = newFiles.size
                val songs = Collections.synchronizedList(mutableListOf<songData>())
                val counter = AtomicInteger(0)
                val semaphore = Semaphore(4) // максимум 4 файла одновременно — оптимально по I/O
                val dispatcher = Dispatchers.IO.limitedParallelism(6)

                coroutineScope {
                    newFiles.forEach { file ->
                        launch(dispatcher) {
                            semaphore.withPermit {
                                try {
                                    val metadata = extractMetadataFromFile(file)

                                    val artPath = grooviqUI.elements.URICoverGetter
                                        .saveAlbumArtPermanentFromUri(file, metadata.album + metadata.year)

                                    val song = songData(
                                        link = file.toString(),
                                        title = metadata.title,
                                        album_original_link = metadata.album,
                                        fileUri = file.toString(),
                                        art_local_link = artPath,
                                        artists = metadata.artistDto,
                                        year = metadata.year,
                                        number = parseTrackNumber(metadata.num),
                                        isExternal = true,
                                        stream = streamInfo(isVideo = metadata?.isVideo ?: false)
                                    )

                                    songs.add(song)

                                    val completed = counter.incrementAndGet()
                                    if (completed % 10 == 0 || completed == total) {
                                        val progress = completed.toFloat() / total.toFloat()
                                        withContext(Dispatchers.Main) {
                                            updateState { state ->
                                                state.copy(
                                                    localFilesFolders = state.localFilesFolders.map {
                                                        if (it.uri == viewFolder.uri)
                                                            it.copy(progressOfLoading = progress)
                                                        else it
                                                    }.toMutableList()
                                                )
                                            }
                                        }
                                    }

                                    // лёгкая пауза для разгрузки GC при больших партиях
                                    if (completed % 200 == 0) delay(50)

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                // группировка и сохранение альбомов после завершения всех задач
                val groupedByAlbum = songs.groupBy { it.album_original_link }
                for ((albumName, albumSongs) in groupedByAlbum) {

                    val sortedSongs = albumSongs.sortedBy { it.number }

                    if (albumSongs.isNotEmpty()) {
                        setAlbumTracks(
                            albumName,
                            sortedSongs,
                            albumName,
                            albumSongs[0].artists,
                            albumSongs[0].year,
                            isExternalSource = true
                        )
                        saveSongsFromSourceToRoom(albumName)
                    }
                }

                // финальное обновление UI
                withContext(Dispatchers.Main) {
                    updateState { state ->
                        state.copy(
                            localFilesFolders = state.localFilesFolders.map {
                                if (it.uri == viewFolder.uri) it.copy(progressOfLoading = 1f)
                                else it
                            }.toMutableList()
                        )
                    }
                }

                saveAllFolders()
                saveAudioSourcesToRoom()

            } catch (e: CancellationException) {

                withContext(Dispatchers.Main) {
                    updateState { state ->
                        state.copy(
                            localFilesFolders = state.localFilesFolders.map {
                                if (it.uri == viewFolder.uri) it.copy(progressOfLoading = 0f)
                                else it
                            }.toMutableList()
                        )
                    }
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                songFromFolderGenerator.remove(viewFolder.uri)
            }

        }
    }


    fun deleteSongsFromFolder(viewFolder: ViewFolder) {

        viewModelScope.launch(Dispatchers.IO) {

            val urisToDelete = findAudioInFolderRecursively(Uri.parse(viewFolder.uri))
            val urisSet = urisToDelete.map { it.toString() }.toSet()
            if (urisSet.isEmpty()) return@launch

            val oldAudioData = uiState.value.audioData
            val oldAllAudioData = uiState.value.allAudioData

            // альбомы с песнями, которые будут удалены
            val albumsWithDeletedSongs = oldAudioData.filterValues { album ->
                album.songIds.any { it in urisSet }
            }

            // оставшиеся песни в альбомах
            val newAlbums = oldAudioData.mapValues { (_, album) ->
                album.copy(songIds = album.songIds.filter { it !in urisSet }.toMutableList())
            }.filterValues { it.songIds.isNotEmpty() }
                .toMutableMap()

            val newAllAudioData = oldAllAudioData.filterValues { song ->
                song.fileUri !in urisSet
            }.toMutableMap()

            // --- Сначала сохраняем альбомы с удаляемыми песнями, чтобы Room удалил их ---
            albumsWithDeletedSongs.keys.forEach { albumName ->
                grooviqUI.elements.URICoverGetter.deleteAlbumArt(albumName, oldAudioData[albumName]?.yearOfAudioSource)
                saveSongsFromSourceToRoom(albumName)
            }

            // --- Затем сохраняем оставшиеся альбомы ---
            newAlbums.keys.forEach { albumName ->
                saveSongsFromSourceToRoom(albumName)
            }

            saveAudioSourcesToRoom()
            saveAllFolders()

            // --- Только после сохранения обновляем UI ---
            val newFolders = uiState.value.localFilesFolders.filter { it.uri != viewFolder.uri }.toMutableList()
            updateState { state ->
                state.copy(
                    allAudioData = newAllAudioData,
                    audioData = newAlbums,
                    localFilesFolders = newFolders
                )
            }
        }
    }



}




