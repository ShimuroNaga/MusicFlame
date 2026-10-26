package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una canción marcada como favorita. Se indexa por el id de MediaStore, igual que antes, cuando
 * los favoritos vivían como un StringSet ("favorite_songs_set") dentro de SharedPreferences.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id") val songId: Long
)
