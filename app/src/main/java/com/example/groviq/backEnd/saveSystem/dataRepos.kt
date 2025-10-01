package com.example.groviq.backEnd.saveSystem

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.ViewFolder
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.songData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DataRepository(private val db: AppDatabase) {

    private val songDao         = db.songDao()
    private val audioDao        = db.audioSourceDao()
    private val folderDao       = db.folderDao()

    fun saveAllAudioAndSources(
        allAudioData: Map<String, songData>,
        audioData: Map<String, audioSource>,
        context: Context
    ) {
        db.runInTransaction {
            val songEntities = allAudioData.values.map { it.toEntity(context) }
            val audioEntities = audioData.map { (key, src) -> src.toEntity(key) }

            songDao.clearAll()
            audioDao.clearAll()

            songDao.insertAll(songEntities)
            audioDao.insertAll(audioEntities)
        }
    }

    suspend fun loadAllAudioSources(context: Context): Triple<
            Map<String, songData>,
            Map<String, audioSource>,
            List<ViewFolder>
            > = coroutineScope {

        val songsDeferred = async(
            Dispatchers.IO) {
            songDao.getAll().associateBy({ it.link }, { it.toDomain(context) })
        }

        val sourcesDeferred = async(Dispatchers.IO) {
            audioDao.getAll().associateBy({ it.key }, { it.toDomain() })
        }

        val foldersDeferred = async(Dispatchers.IO) {
            folderDao.getAll().map { it.toDomain() }
        }

        Triple(
            songsDeferred.await(),
            sourcesDeferred.await(),
            foldersDeferred.await()
        )
    }

    suspend fun saveFolders(folders: List<ViewFolder>) {
        folderDao.clearAll()
        folderDao.insertAll(folders.map { it.toEntity() })
    }

    @OptIn(
        UnstableApi::class
    )
    fun saveSong(playerViewModel: PlayerViewModel, song: songData, context: Context) {

        //we have to make decision, to keep this entity, or delete
        if (playerViewModel.isSongSavable(song))
        {
            val entity = song.toEntity(context)
            songDao.insert(entity)
        }
        else
        {
            val entity = song.toEntity(context)
            songDao.delete(entity)
        }

    }

    @OptIn(
        UnstableApi::class
    )
    fun saveAudioSources(playerViewModel: PlayerViewModel) {

        val existing = audioDao.getAll()

        val saveableSources = playerViewModel.getSavableAudioSources()

        audioDao.clearAll()

        saveableSources.entries.forEach { source ->
            audioDao.insert(source.value.toEntity(source.key))
        }
    }

}
