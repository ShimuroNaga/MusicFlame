package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtworkCacheDao {

    @Query("SELECT * FROM artwork_cache")
    suspend fun getAll(): List<ArtworkCacheEntity>

    @Query("SELECT COUNT(*) FROM artwork_cache")
    suspend fun count(): Int

    /** Escrituras normales (set): gana el valor más nuevo. Room las corre dentro de una transacción. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ArtworkCacheEntity>)

    /** Migración desde el JSON viejo: si la fila ya existe, NO se pisa con el dato viejo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(entities: List<ArtworkCacheEntity>)

    @Query("DELETE FROM artwork_cache WHERE file_path IN (:filePaths)")
    suspend fun deleteAll(filePaths: List<String>)
}
