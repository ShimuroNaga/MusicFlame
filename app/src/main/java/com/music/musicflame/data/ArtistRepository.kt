package com.music.musicflame.data

// Mismo patrón que Album (ver AlbumRepository.kt): un artista es simplemente
// el resultado de agrupar la librería completa por Song.artist. La carátula
// "representativa" se elige igual que en Album: la primera canción (en orden
// ya estable, por título) que sí tenga carátula, para que el resultado sea
// determinista y no dependa del orden arbitrario de escaneo de MediaStore.
data class Artist(
    val name: String,
    val albumArtUri: String?,
    val albumArtSourcePath: String? = null,
    val albumArtIsCustom: Boolean = false,
    val songs: List<Song>
) {
    val songCount: Int get() = songs.size
    val albumCount: Int get() = songs.map { it.album }.distinct().size
}

fun groupSongsIntoArtists(songs: List<Song>): List<Artist> {
    return songs
        .groupBy { it.artist }
        .map { (artistName, songsByArtist) ->
            val sortedSongs = songsByArtist.sortedBy { it.title }
            val artSourceSong = sortedSongs.firstOrNull { it.albumArtUri != null }
            Artist(
                name = artistName,
                albumArtUri = artSourceSong?.albumArtUri,
                albumArtSourcePath = artSourceSong?.path,
                albumArtIsCustom = artSourceSong?.hasCustomCover ?: false,
                songs = sortedSongs
            )
        }
        .sortedBy { it.name.lowercase() }
}
