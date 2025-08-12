package com.example.groviq.backEnd.saveSystem

import android.content.Context
import com.example.groviq.backEnd.dataStructures.audioSource
import com.example.groviq.backEnd.dataStructures.songData

class DataRepository(private val db: AppDatabase) {

    private val songDao = db.songDao()
    private val audioDao = db.audioSourceDao()

    // Сохранить ВСЕ карты atomically
    fun saveAllAudioAndSources(
        allAudioData: Map<String, songData>,
        audioData: Map<String, audioSource>,
        context: Context
    ) {
        db.runInTransaction {
            // преобразуем в сущности
            val songEntities = allAudioData.values.map { it.toEntity(context) }
            val audioEntities = audioData.map { (key, src) -> src.toEntity(key) }

            songDao.clearAll()
            audioDao.clearAll()

            // Записываем списками
            songDao.insertAll(songEntities)
            audioDao.insertAll(audioEntities)
        }
    }

    // Загрузить всё
    fun loadAllAudioAndSources(context: Context): Pair<Map<String, songData>, Map<String, audioSource>> {
        val songs = songDao.getAll().associateBy({ it.link }, { it.toDomain(context) })
        val sources = audioDao.getAll().associateBy({ it.key }, { it.toDomain() })
        return Pair(songs, sources)
    }

    // инкрементное сохранение одной песни / аудиосурса
    fun saveSong(song: songData, context: Context) {
        val entity = song.toEntity(context)
        songDao.insert(entity)
    }

    fun loadAllSongs(context: Context): List<songData> {
        return songDao.getAll().map { it.toDomain(context) }
    }

    fun saveAudioSource(key: String, source: audioSource) {
        audioDao.insert(source.toEntity(key))
    }

}
