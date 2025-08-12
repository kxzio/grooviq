package com.example.groviq.backEnd.saveSystem

import androidx.room.*

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs")
    fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE link = :link LIMIT 1")
    fun getByLink(link: String): SongEntity?

    @Query("DELETE FROM songs")
    fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(song: SongEntity)
}

@Dao
interface AudioSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(sources: List<AudioSourceEntity>)

    @Query("SELECT * FROM audio_sources")
    fun getAll(): List<AudioSourceEntity>

    @Query("DELETE FROM audio_sources")
    fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(source: AudioSourceEntity)
}
