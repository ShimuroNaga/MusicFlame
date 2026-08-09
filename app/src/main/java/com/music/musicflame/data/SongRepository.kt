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
    val album: String = "Desconocido",
    val duration: Long,
    val path: String,
    val albumArtUri: String? = null,
    val dateAdded: Long = 0L,
    val youtubeVideoId: String? = null // <-- NUEVO: ID real del video en YouTube (distinto de `id`)
)

fun loadSongsFromDevice(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val settingsRepo = SettingsRepository(context)
    // Carátulas y nombres personalizados por el usuario (ver SongCustomizationRepository).
    // Se aplican aquí para que TODAS las pantallas (canciones, álbumes, playlists, mix...)
    // muestren siempre la versión personalizada sin tener que tocarlas una por una.
    val customizations = SongCustomizationRepository(context).getAll()

    val minSeconds = settingsRepo.getDurationFilterMin()
    val maxSeconds = settingsRepo.getDurationFilterMax()
    val filterMode = settingsRepo.getDurationFilterMode()

    val minMs = minSeconds * 1000L
    val maxMs = if (maxSeconds == Int.MAX_VALUE) Long.MAX_VALUE else maxSeconds * 1000L

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
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
        val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (it.moveToNext()) {
            val duration = it.getLong(durationCol)
            val fallsInsideRange = duration in minMs..maxMs
            val shouldInclude = if (filterMode == "only") fallsInsideRange else !fallsInsideRange

            if (shouldInclude) {
                val id = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                val defaultAlbumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()

                val customization = customizations[id]

                songs.add(
                    Song(
                        id = id,
                        title = customization?.title ?: (it.getString(titleCol) ?: "Desconocido"),
                        artist = it.getString(artistCol) ?: "Artista Desconocido",
                        album = it.getString(albumCol) ?: "Desconocido",
                        duration = duration,
                        path = it.getString(pathCol) ?: "",
                        albumArtUri = customization?.coverUri ?: defaultAlbumArtUri,
                        dateAdded = it.getLong(dateAddedCol)
                        // youtubeVideoId queda null para canciones locales, es lo esperado
                    )
                )
            }
        }
    }
    return songs
}

/** Consulta a MediaStore el título ORIGINAL de una canción (ignorando cualquier personalización). */
fun getOriginalSongTitle(context: Context, songId: Long): String {
    val projection = arrayOf(MediaStore.Audio.Media.TITLE)
    val selection = "${MediaStore.Audio.Media._ID} = ?"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, arrayOf(songId.toString()), null
    )?.use { c ->
        if (c.moveToFirst()) {
            return c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Desconocido"
        }
    }
    return "Desconocido"
}

/** Consulta a MediaStore la carátula ORIGINAL (por álbum) de una canción, sin personalización. */
fun getDefaultAlbumArtUri(context: Context, songId: Long): String? {
    val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
    val selection = "${MediaStore.Audio.Media._ID} = ?"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, arrayOf(songId.toString()), null
    )?.use { c ->
        if (c.moveToFirst()) {
            val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
            return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            ).toString()
        }
    }
    return null
}