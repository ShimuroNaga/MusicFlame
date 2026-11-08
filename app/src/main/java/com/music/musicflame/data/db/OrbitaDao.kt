package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class OrbitaDao {

    @Query(
        "SELECT m.id AS id, m.nombre AS nombre, m.autor_nombre AS autor_nombre, " +
            "m.es_propia AS es_propia, m.fecha_import AS fecha_import, " +
            "(SELECT COUNT(*) FROM orbita_song s WHERE s.orbita_id = m.id) AS cantidad " +
            "FROM orbita_meta m ORDER BY m.es_propia DESC, m.fecha_import DESC"
    )
    abstract suspend fun listWithCount(): List<OrbitaMetaWithCount>

    @Query("SELECT * FROM orbita_meta WHERE es_propia = 1 LIMIT 1")
    abstract suspend fun getOwn(): OrbitaMetaEntity?

    @Query("SELECT * FROM orbita_meta WHERE es_propia = 0 AND autor_nombre = :autor COLLATE NOCASE LIMIT 1")
    abstract suspend fun findImportedByAuthor(autor: String): OrbitaMetaEntity?

    @Query("SELECT * FROM orbita_meta WHERE id = :id")
    abstract suspend fun getMeta(id: Long): OrbitaMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMeta(meta: OrbitaMetaEntity): Long

    @Query("DELETE FROM orbita_meta WHERE id = :id")
    abstract suspend fun deleteMeta(id: Long)

    @Query("SELECT * FROM orbita_song WHERE orbita_id = :orbitaId ORDER BY fecha_agregada ASC, id ASC")
    abstract suspend fun songsOf(orbitaId: Long): List<OrbitaSongEntity>

    @Query("SELECT COUNT(*) FROM orbita_song WHERE orbita_id = :orbitaId")
    abstract suspend fun countOf(orbitaId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSong(song: OrbitaSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSongs(songs: List<OrbitaSongEntity>)

    @Query("UPDATE orbita_song SET pos_x = :x, pos_y = :y WHERE id = :id")
    abstract suspend fun updatePos(id: Long, x: Float, y: Float)

    @Query("UPDATE orbita_song SET nota = :nota WHERE id = :id")
    abstract suspend fun updateNota(id: Long, nota: String)

    @Query("DELETE FROM orbita_song WHERE id = :id")
    abstract suspend fun deleteSong(id: Long)

    @Query("DELETE FROM orbita_song WHERE orbita_id = :orbitaId")
    abstract suspend fun deleteSongsOf(orbitaId: Long)

    /** Borra una órbita completa (nodos + meta) en una sola transacción. */
    @Transaction
    open suspend fun deleteOrbita(orbitaId: Long) {
        deleteSongsOf(orbitaId)
        deleteMeta(orbitaId)
    }

    /** Crea una órbita importada con todos sus nodos; devuelve su id. */
    @Transaction
    open suspend fun createImported(meta: OrbitaMetaEntity, songs: List<OrbitaSongEntity>): Long {
        val id = insertMeta(meta.copy(id = 0))
        if (songs.isNotEmpty()) insertSongs(songs.map { it.copy(id = 0, orbitaId = id) })
        return id
    }

    /** Reemplaza el contenido de una órbita importada ya existente (misma id). */
    @Transaction
    open suspend fun replaceImported(meta: OrbitaMetaEntity, songs: List<OrbitaSongEntity>) {
        deleteSongsOf(meta.id)
        insertMeta(meta)
        if (songs.isNotEmpty()) insertSongs(songs.map { it.copy(id = 0, orbitaId = meta.id) })
    }
}
