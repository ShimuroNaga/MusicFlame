package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un "Momento" (fragmento favorito) de una canción. Se indexa por la RUTA del archivo, no por el id de MediaStore. */
@Entity(tableName = "moments", indices = [Index(value = ["path"])])
data class MomentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
