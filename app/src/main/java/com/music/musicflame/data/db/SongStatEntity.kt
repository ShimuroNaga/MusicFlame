package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Estadísticas de reproducción de una canción (clave: id de MediaStore). */
@Entity(tableName = "song_stats")
data class SongStatEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "total_listened_ms") val totalListenedMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long
)
