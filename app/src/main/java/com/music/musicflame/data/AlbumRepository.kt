package com.music.musicflame.data

data class Album(
    val name: String,
    val artist: String,
    val albumArtUri: String?,
    val songs: List<Song>,
    // Ruta física del archivo cuya carátula se usa para representar el álbum
    // (misma canción que aportó albumArtUri). Sirve para que la UI pueda
    // extraer la carátula embebida directamente del archivo si albumArtUri
    // falla en cargar (ver AlbumArt.kt / EmbeddedAlbumArtFetcher).
    val albumArtSourcePath: String? = null
) {
    val songCount: Int get() = songs.size
    val totalDurationMs: Long get() = songs.sumOf { it.duration }
}

// ANTES: se agrupaba SOLO por `it.album` (el nombre, como texto). Si dos
// canciones de artistas distintos terminaban con el mismo nombre de álbum
// (por ejemplo, al renombrar el álbum de varias canciones de "Rufus" a un
// nombre que coincide, exacto o por may/min, con el de un álbum ya existente
// de otro artista), groupBy las fusionaba en un solo Album sin distinguir
// artista. Encima, la carátula del grupo se tomaba con
// `songsInAlbum.firstOrNull { it.albumArtUri != null }` — es decir, la
// carátula de la PRIMERA canción del grupo en el orden en que venían (que no
// tiene por qué ser una canción de Rufus), así que la carátula que se veía
// podía terminar siendo la de un álbum "que nada que ver", aunque el archivo
// físico de cada mp3 de Rufus tuviera su carátula original correcta.
//
// AHORA: se agrupa por (álbum, artista) para no mezclar álbumes con el mismo
// nombre pero de artistas distintos, y la carátula se elige de forma
// determinista (mismo orden que se muestra la lista de canciones del álbum,
// no el orden arbitrario de escaneo), priorizando la primera canción CON
// carátula dentro de ese orden ya estable.
fun groupSongsIntoAlbums(songs: List<Song>): List<Album> {
    return songs
        .groupBy { it.album to it.artist }
        .map { (key, songsInAlbum) ->
            val (albumName, artistName) = key
            val sortedSongs = songsInAlbum.sortedBy { it.title }
            val artSourceSong = sortedSongs.firstOrNull { it.albumArtUri != null }
            Album(
                name = albumName,
                artist = artistName,
                albumArtUri = artSourceSong?.albumArtUri,
                songs = sortedSongs,
                albumArtSourcePath = artSourceSong?.path
            )
        }
        .sortedBy { it.name.lowercase() }
}