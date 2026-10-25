package com.music.musicflame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY position")
    abstract suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlist_id, position")
    abstract suspend fun getSongRows(): List<PlaylistSongEntity>

    @Query("SELECT COUNT(*) FROM playlists")
    abstract suspend fun countPlaylists(): Int

    @Query("SELECT COUNT(*) FROM playlist_songs")
    abstract suspend fun countSongRows(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSongRows(rows: List<PlaylistSongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaylistsIgnoring(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongRowsIgnoring(rows: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs")
    abstract suspend fun deleteAllSongRows()

    @Query("DELETE FROM playlists")
    abstract suspend fun deleteAllPlaylists()

    /** Reemplaza TODO el contenido por esta foto (una sola transacción: o queda todo o no cambia nada). */
    @Transaction
    open suspend fun replaceAll(playlists: List<PlaylistEntity>, rows: List<PlaylistSongEntity>) {
        deleteAllSongRows()
        deleteAllPlaylists()
        if (playlists.isNotEmpty()) insertPlaylists(playlists)
        if (rows.isNotEmpty()) insertSongRows(rows)
    }

    /** Migración desde el JSON viejo: no pisa filas que ya existan. Atómica. */
    @Transaction
    open suspend fun migrateAll(playlists: List<PlaylistEntity>, rows: List<PlaylistSongEntity>) {
        if (playlists.isNotEmpty()) insertPlaylistsIgnoring(playlists)
        if (rows.isNotEmpty()) insertSongRowsIgnoring(rows)
    }
}
