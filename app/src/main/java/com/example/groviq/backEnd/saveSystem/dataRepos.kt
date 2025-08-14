package com.example.groviq.backEnd.saveSystem

import android.content.Context
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.songData

class DataRepository(private val db: AppDatabase) {

    private val songDao = db.songDao()
    private val audioDao = db.audioSourceDao()

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

    fun loadAllAudioAndSources(context: Context): Pair<Map<String, songData>, Map<String, audioSource>> {
        val songs       = songDao   .getAll().associateBy({ it.link }, { it.toDomain(context) })
        val sources     = audioDao  .getAll().associateBy({ it.key }, { it.toDomain() })
        return Pair(songs, sources)
    }

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

    fun saveAudioSources(playerViewModel: PlayerViewModel) {

        val existing = audioDao.getAll()

        val saveableSources = playerViewModel.getSavableAudioSources()

        val newList = saveableSources
            .map { it.value.toEntity(it.key) }

        if (existing.size == newList.size && existing.containsAll(newList) && newList.containsAll(existing)) {
            return
        }

        audioDao.clearAll()

        saveableSources.entries.forEach { source ->
            audioDao.insert(source.value.toEntity(source.key))
        }
    }

}
