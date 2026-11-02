package com.music.musicflame.data

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Desconocido",
    val duration: Long,
    val path: String,
    val albumArtUri: String? = null,
    // NUEVO: true cuando `albumArtUri` viene de una carátula elegida a mano
    // por el usuario (SongCustomization.coverUri), false cuando es la URI
    // "de fábrica" de MediaStore por álbum (content://.../albumart/{albumId}).
    // AlbumArt() usa este dato para decidir si respeta esa URI o si prueba
    // primero la carátula embebida en el propio archivo (ver AlbumArt.kt).
    val hasCustomCover: Boolean = false,
    // NUEVO: título de visualización con códigos § (SongCustomization.displayTitleFormatted).
    // null cuando la canción no tiene uno guardado; en ese caso las cards deben
    // mostrar `title` tal cual (ver FormatCodeText.kt / rawDisplayTitle()).
    val displayTitleFormatted: String? = null,
    val dateAdded: Long = 0L,
    val youtubeVideoId: String? = null, // <-- NUEVO: ID real del video en YouTube (distinto de `id`)
    // --- NUEVO: usados por el buscador con filtros (Artista/Álbum/Año/Género) ---
    val year: Int? = null,
    val genre: String? = null
)
