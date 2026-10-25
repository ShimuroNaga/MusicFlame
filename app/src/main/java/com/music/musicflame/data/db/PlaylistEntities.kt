package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Una playlist REGULAR creada por el usuario. `position` conserva el orden de la lista. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "custom_cover_uri") val customCoverUri: String?,
    @ColumnInfo(name = "position") val position: Int
)

/** Una canción dentro de una playlist. `position` conserva el orden de las canciones. */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "song_id") val songId: Long
)
