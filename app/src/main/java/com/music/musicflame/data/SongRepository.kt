package com.music.musicflame.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val albumArtUri: String? = null,
    val dateAdded: Long = 0L
)

fun loadSongsFromDevice(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val settingsRepo = SettingsRepository(context)

    val minSeconds = settingsRepo.getDurationFilterMin()
    val maxSeconds = settingsRepo.getDurationFilterMax()
    val filterMode = settingsRepo.getDurationFilterMode()

    val minMs = minSeconds * 1000L
    val maxMs = if (maxSeconds == Int.MAX_VALUE) Long.MAX_VALUE else maxSeconds * 1000L

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATE_ADDED
    )

    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
    )

    cursor?.use {
        val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (it.moveToNext()) {
            val duration = it.getLong(durationCol)
            val fallsInsideRange = duration in minMs..maxMs
            val shouldInclude = if (filterMode == "only") fallsInsideRange else !fallsInsideRange

            if (shouldInclude) {
                val albumId = it.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()

                songs.add(
                    Song(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol) ?: "Desconocido",
                        artist = it.getString(artistCol) ?: "Artista Desconocido",
                        duration = duration,
                        path = it.getString(pathCol) ?: "",
                        albumArtUri = albumArtUri,
                        dateAdded = it.getLong(dateAddedCol)
                    )
                )
            }
        }
    }
    return songs
}