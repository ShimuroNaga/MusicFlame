package com.music.musicflame.data

data class Album(
    val name: String,
    val artist: String,
    val albumArtUri: String?,
    val songs: List<Song>
) {
    val songCount: Int get() = songs.size
    val totalDurationMs: Long get() = songs.sumOf { it.duration }
}

fun groupSongsIntoAlbums(songs: List<Song>): List<Album> {
    return songs
        .groupBy { it.album }
        .map { (albumName, songsInAlbum) ->
            Album(
                name = albumName,
                artist = songsInAlbum.first().artist,
                albumArtUri = songsInAlbum.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                songs = songsInAlbum.sortedBy { it.title }
            )
        }
        .sortedBy { it.name.lowercase() }
}