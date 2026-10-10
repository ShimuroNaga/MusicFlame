package com.music.musicflame.data

// Género ya venía siendo leído por SongRepository.loadGenreMapFromDevice()
// (MediaStore.Audio.Genres + Genres.Members) y guardado en Song.genre, pero
// hasta ahora solo se usaba como filtro dentro del buscador de SongScreen.
// No todos los archivos tienen género etiquetado; los que no lo tienen caen
// en el balde "Sin género", mismo criterio que Album usa "Desconocido" para
// álbum/artista faltante.
const val SIN_GENERO = "Sin género"

/** SIN_GENERO es la clave interna del balde; esto devuelve el texto visible según el idioma. */
@androidx.compose.runtime.Composable
fun genreDisplayName(name: String): String =
    if (name == SIN_GENERO) androidx.compose.ui.res.stringResource(com.music.musicflame.R.string.genre_none) else name

data class Genre(
    val name: String,
    val songs: List<Song>
) {
    val songCount: Int get() = songs.size
}

fun groupSongsIntoGenres(songs: List<Song>): List<Genre> {
    return songs
        .groupBy { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } ?: SIN_GENERO }
        .map { (genreName, songsInGenre) ->
            Genre(name = genreName, songs = songsInGenre.sortedBy { it.title })
        }
        .sortedBy { it.name.lowercase() }
}
