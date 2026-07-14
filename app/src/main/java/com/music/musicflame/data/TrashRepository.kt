package com.music.musicflame.data

import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashedSong(
    val song: Song,
    val deletedAt: Long
)

data class TrashedPlaylist(
    val playlist: Playlist,
    val deletedAt: Long
)

class TrashRepository(private val context: Context) { // Hacemos el context accesible en toda la clase
    private val prefs = context.getSharedPreferences("trash", Context.MODE_PRIVATE)
    private val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    fun getTrash(): List<TrashedSong> {
        val json = prefs.getString("trash", "[]") ?: "[]"
        val array = JSONArray(json)
        val result = mutableListOf<TrashedSong>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                TrashedSong(
                    song = Song(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        duration = obj.getLong("duration"),
                        path = obj.getString("path")
                    ),
                    deletedAt = obj.getLong("deletedAt")
                )
            )
        }
        return result
    }

    // --- NUEVO MÉTODO INTELIGENTE PARA LA SELECCIÓN MÚLTIPLE ---
    fun moveToTrash(songs: List<Song>) {
        if (songs.isEmpty()) return

        val trash = getTrash().toMutableList()
        var movedCount = 0
        val now = System.currentTimeMillis()

        songs.forEach { song ->
            if (trash.none { it.song.id == song.id }) {
                trash.add(TrashedSong(song, now))
                movedCount++
            }
        }

        saveTrash(trash)

        // El mismo repositorio se encarga de avisar al usuario
        Toast.makeText(context, "$movedCount canciones movidas a la papelera", Toast.LENGTH_SHORT).show()
    }

    fun addToTrash(song: Song) {
        val trash = getTrash().toMutableList()
        if (trash.none { it.song.id == song.id }) {
            trash.add(TrashedSong(song, System.currentTimeMillis()))
            saveTrash(trash)
        }
    }

    fun deleteFromTrash(songId: Long) {
        val items = getTrash().filter { it.song.id != songId }
        saveTrash(items)
    }

    fun restoreSong(songId: Long) {
        deleteFromTrash(songId)
    }

    fun clearAll() {
        val trash = getTrash()
        trash.forEach { File(it.song.path).delete() }
        saveTrash(emptyList())
    }

    fun purgeExpired() {
        val now = System.currentTimeMillis()
        val trash = getTrash()
        val expired = trash.filter { now - it.deletedAt > thirtyDaysMs }
        expired.forEach { File(it.song.path).delete() }
        saveTrash(trash.filter { now - it.deletedAt <= thirtyDaysMs })

        // Playlists en papelera: solo se limpia la entrada, NUNCA se tocan las canciones
        val trashPlaylists = getTrashedPlaylists()
        saveTrashedPlaylists(trashPlaylists.filter { now - it.deletedAt <= thirtyDaysMs })
    }

    // --- PAPELERA DE PLAYLISTS (solo borra el contenedor, jamás las canciones dentro) ---
    fun getTrashedPlaylists(): List<TrashedPlaylist> {
        val json = prefs.getString("trash_playlists", "[]") ?: "[]"
        val array = JSONArray(json)
        val result = mutableListOf<TrashedPlaylist>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val songIds = mutableListOf<Long>()
            val songsArray = obj.getJSONArray("songIds")
            for (j in 0 until songsArray.length()) songIds.add(songsArray.getLong(j))
            result.add(
                TrashedPlaylist(
                    playlist = Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        songIds = songIds,
                        customCoverUri = if (obj.has("customCoverUri")) obj.getString("customCoverUri") else null
                    ),
                    deletedAt = obj.getLong("deletedAt")
                )
            )
        }
        return result
    }

    fun trashPlaylist(playlist: Playlist) {
        val trash = getTrashedPlaylists().toMutableList()
        if (trash.none { it.playlist.id == playlist.id }) {
            trash.add(TrashedPlaylist(playlist, System.currentTimeMillis()))
            saveTrashedPlaylists(trash)
        }
    }

    fun restorePlaylistFromTrash(playlistId: String) {
        val items = getTrashedPlaylists().filter { it.playlist.id != playlistId }
        saveTrashedPlaylists(items)
    }

    fun deletePlaylistPermanently(playlistId: String) {
        val items = getTrashedPlaylists().filter { it.playlist.id != playlistId }
        saveTrashedPlaylists(items)
    }

    private fun saveTrashedPlaylists(trash: List<TrashedPlaylist>) {
        val array = JSONArray()
        trash.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.playlist.id)
            obj.put("name", item.playlist.name)
            if (item.playlist.customCoverUri != null) obj.put("customCoverUri", item.playlist.customCoverUri)
            val songs = JSONArray()
            item.playlist.songIds.forEach { songs.put(it) }
            obj.put("songIds", songs)
            obj.put("deletedAt", item.deletedAt)
            array.put(obj)
        }
        prefs.edit().putString("trash_playlists", array.toString()).apply()
    }

    fun daysRemaining(deletedAt: Long): Int {
        val elapsed = System.currentTimeMillis() - deletedAt
        val remaining = thirtyDaysMs - elapsed
        return (remaining / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    private fun saveTrash(trash: List<TrashedSong>) {
        val array = JSONArray()
        trash.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.song.id)
            obj.put("title", item.song.title)
            obj.put("artist", item.song.artist)
            obj.put("duration", item.song.duration)
            obj.put("path", item.song.path)
            obj.put("deletedAt", item.deletedAt)
            array.put(obj)
        }
        prefs.edit().putString("trash", array.toString()).apply()
    }
}