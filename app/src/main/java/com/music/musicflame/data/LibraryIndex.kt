package com.music.musicflame.data

/**
 * Índice inmutable de la biblioteca, construido UNA vez por escaneo (ver SongLibraryHolder).
 *
 * ANTES: cada pantalla que necesitaba "buscar la canción con id X", "agrupar por álbum/artista/
 * género" lo recalculaba por su cuenta (AlbumScreen agrupaba 3 veces en el hilo principal en
 * cada cambio de librería; MusicPlayerManager/MusicPlaybackService/AnomalyScanDialog hacían
 * songs.find { it.id == ... } lineal).
 *
 * AHORA: todo se deriva de esta clase con `lazy` (seguro entre hilos). SongLibraryHolder dispara
 * [warmUp] en Dispatchers.Default justo después de publicar la lista, así que cuando una pantalla
 * lee `albums`/`artists`/`genres` casi siempre ya están calculados; si llega antes, espera al
 * mismo cálculo en vez de duplicarlo.
 */
class LibraryIndex(val songs: List<Song>) {

    val byId: Map<Long, Song> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val m = HashMap<Long, Song>(songs.size * 4 / 3 + 1)
        for (s in songs) m[s.id] = s
        m
    }

    val byPath: Map<String, Song> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val m = HashMap<String, Song>(songs.size * 4 / 3 + 1)
        for (s in songs) if (s.path.isNotEmpty()) m[s.path] = s
        m
    }

    val albums: List<Album> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { groupSongsIntoAlbums(songs) }
    val artists: List<Artist> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { groupSongsIntoArtists(songs) }
    val genres: List<Genre> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { groupSongsIntoGenres(songs) }

    /** Fuerza el cálculo de todo lo derivado. Llamar desde un hilo de fondo. */
    fun warmUp() {
        byId; byPath; albums; artists; genres
    }
}
