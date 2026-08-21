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
    val youtubeVideoId: String? = null, // <-- NUEVO: ID real del video en YouTube (distinto de `id`)
    // --- NUEVO: usados por el buscador con filtros (Artista/Álbum/Año/Género) ---
    val year: Int? = null,
    val genre: String? = null
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

    // Mapa songId -> nombre de género, usado por el buscador con filtros.
    // Se arma aparte porque el género no vive en la tabla Media sino en
    // MediaStore.Audio.Genres (funciona en todas las versiones de Android,
    // a diferencia de la columna GENRE directa que solo existe desde API 30).
    val genreBySongId = loadGenreMapFromDevice(context)

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.YEAR
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
        val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

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
                val year = it.getInt(yearCol).takeIf { y -> y > 0 }

                songs.add(
                    Song(
                        id = id,
                        title = customization?.title ?: (it.getString(titleCol) ?: "Desconocido"),
                        artist = customization?.artist ?: (it.getString(artistCol) ?: "Artista Desconocido"),
                        album = customization?.album ?: (it.getString(albumCol) ?: "Desconocido"),
                        duration = duration,
                        path = it.getString(pathCol) ?: "",
                        albumArtUri = customization?.coverUri ?: defaultAlbumArtUri,
                        dateAdded = it.getLong(dateAddedCol),
                        // youtubeVideoId queda null para canciones locales, es lo esperado
                        year = year,
                        genre = genreBySongId[id]
                    )
                )
            }
        }
    }
    return songs
}

/**
 * Recorre MediaStore.Audio.Genres y, para cada género, sus canciones miembro
 * (MediaStore.Audio.Genres.Members), armando un mapa songId -> nombre de género.
 * No todos los archivos tienen género etiquetado; los que no aparecen en este
 * mapa simplemente no tendrán filtro de género disponible.
 */
private fun loadGenreMapFromDevice(context: Context): Map<Long, String> {
    val result = mutableMapOf<Long, String>()
    try {
        val genresCursor = context.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null, null, null
        )
        genresCursor?.use { gc ->
            val genreIdCol = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val genreNameCol = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (gc.moveToNext()) {
                val genreId = gc.getLong(genreIdCol)
                val genreName = gc.getString(genreNameCol)?.trim()
                if (genreName.isNullOrEmpty()) continue

                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                val membersCursor = context.contentResolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null, null, null
                )
                membersCursor?.use { mc ->
                    val audioIdCol = mc.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (mc.moveToNext()) {
                        result[mc.getLong(audioIdCol)] = genreName
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Si el dispositivo/fabricante no soporta bien esta tabla, simplemente
        // no habrá filtro de género disponible en vez de romper la carga de canciones.
        e.printStackTrace()
    }
    return result
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

/** Consulta a MediaStore el artista ORIGINAL de una canción (ignorando cualquier personalización). */
fun getOriginalSongArtist(context: Context, songId: Long): String {
    val projection = arrayOf(MediaStore.Audio.Media.ARTIST)
    val selection = "${MediaStore.Audio.Media._ID} = ?"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, arrayOf(songId.toString()), null
    )?.use { c ->
        if (c.moveToFirst()) {
            return c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Artista Desconocido"
        }
    }
    return "Artista Desconocido"
}

/** Consulta a MediaStore el álbum ORIGINAL de una canción (ignorando cualquier personalización). */
fun getOriginalSongAlbum(context: Context, songId: Long): String {
    val projection = arrayOf(MediaStore.Audio.Media.ALBUM)
    val selection = "${MediaStore.Audio.Media._ID} = ?"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, arrayOf(songId.toString()), null
    )?.use { c ->
        if (c.moveToFirst()) {
            return c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Desconocido"
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