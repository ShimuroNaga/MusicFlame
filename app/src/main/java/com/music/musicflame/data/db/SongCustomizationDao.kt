package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongCustomizationDao {
    @Query("SELECT * FROM song_customizations")
    suspend fun getAll(): List<SongCustomizationEntity>

    @Query("SELECT COUNT(*) FROM song_customizations")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SongCustomizationEntity>)

    /** Migración desde el JSON viejo: si la fila ya existe, NO se pisa con el dato viejo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(entities: List<SongCustomizationEntity>)

    @Query("DELETE FROM song_customizations WHERE song_id IN (:songIds)")
    suspend fun deleteAll(songIds: List<Long>)
}
