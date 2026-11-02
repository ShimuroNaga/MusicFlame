package com.music.musicflame.data

/**
 * Cruza las rutas de un .m3u con la biblioteca. Misma regla que antes
 * (`song.path == path || song.path.endsWith(path.trimStart('/'))`) pero en O(canciones + rutas)
 * en vez de O(canciones × rutas): indexa las rutas del m3u por nombre de archivo y solo compara
 * el sufijo contra las pocas que comparten nombre.
 */
fun matchM3uPaths(songs: List<Song>, paths: List<String>): List<Song> {
    if (songs.isEmpty() || paths.isEmpty()) return emptyList()
    val exact = HashSet<String>(paths.size * 4 / 3 + 1)
    val suffixesByName = HashMap<String, MutableList<String>>()
    for (p in paths) {
        exact.add(p)
        val trimmed = p.trimStart('/')
        suffixesByName.getOrPut(trimmed.substringAfterLast('/')) { ArrayList(1) }.add(trimmed)
    }
    return songs.filter { song ->
        song.path in exact ||
            suffixesByName[song.path.substringAfterLast('/')]?.any { song.path.endsWith(it) } == true
    }
}
