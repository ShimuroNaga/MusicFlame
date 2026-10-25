package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Personalización de una canción (clave: id de MediaStore). Los campos null = "sin personalizar". */
@Entity(tableName = "song_customizations")
data class SongCustomizationEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo(name = "artist") val artist: String?,
    @ColumnInfo(name = "album") val album: String?,
    @ColumnInfo(name = "display_title_formatted") val displayTitleFormatted: String?
)
