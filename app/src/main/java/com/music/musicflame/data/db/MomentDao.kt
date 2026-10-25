package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class MomentDao {

    @Query("SELECT * FROM moments")
    abstract suspend fun getAll(): List<MomentEntity>

    @Query("SELECT COUNT(*) FROM moments")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<MomentEntity>)

    /** Migración desde el JSON viejo: si la fila ya existe, NO se pisa con el dato viejo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAllIgnoringExisting(entities: List<MomentEntity>)

    @Query("DELETE FROM moments WHERE path IN (:paths)")
    abstract suspend fun deleteForPaths(paths: List<String>)

    /** Reemplaza los momentos de esas rutas por exactamente `entities` (una sola transacción). */
    @Transaction
    open suspend fun replaceForPaths(paths: List<String>, entities: List<MomentEntity>) {
        paths.chunked(500).forEach { deleteForPaths(it) }
        if (entities.isNotEmpty()) insertAll(entities)
    }
}
