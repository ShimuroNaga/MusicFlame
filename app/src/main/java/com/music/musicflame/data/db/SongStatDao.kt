package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongStatDao {
    @Query("SELECT * FROM song_stats")
    suspend fun getAll(): List<SongStatEntity>

    @Query("SELECT COUNT(*) FROM song_stats")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SongStatEntity>)

    /** Migración desde el JSON viejo: si la fila ya existe, NO se pisa con el dato viejo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(entities: List<SongStatEntity>)
}
