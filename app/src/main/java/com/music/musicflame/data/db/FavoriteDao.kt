package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class FavoriteDao {

    @Query("SELECT * FROM favorites")
    abstract suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<FavoriteEntity>)

    /** Migración desde el StringSet viejo: si la fila ya existe, NO se pisa. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAllIgnoringExisting(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE song_id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    /** Quita [removed] y agrega [added] en una sola transacción. */
    @Transaction
    open suspend fun applyChanges(removed: List<Long>, added: List<FavoriteEntity>) {
        removed.chunked(500).forEach { deleteByIds(it) }
        if (added.isNotEmpty()) insertAll(added)
    }
}
