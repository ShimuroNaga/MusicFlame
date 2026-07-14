package com.music.musicflame.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val isDefault: Boolean = false,
    val customCoverUri: String? = null
)

class PlaylistRepository(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun getPlaylists(): List<Playlist> {
        return getAllPlaylists()
    }

    fun getAllPlaylists(): List<Playlist> {
        val json = prefs.getString("playlists", "[]") ?: "[]"
        val array = JSONArray(json)
        val result = mutableListOf<Playlist>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val songIds = mutableListOf<Long>()
            val songs = obj.getJSONArray("songIds")
            for (j in 0 until songs.length()) {
                songIds.add(songs.getLong(j))
            }
            result.add(
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    songIds = songIds,
                    isDefault = obj.optBoolean("isDefault", false),
                    customCoverUri = if (obj.has("customCoverUri")) obj.getString("customCoverUri") else null
                )
            )
        }
        return result
    }
    fun createPlaylist(name: String) {
        val playlists = getAllPlaylists().toMutableList()
        playlists.add(
            Playlist(
                id = System.currentTimeMillis().toString(),
                name = name,
                songIds = emptyList()
            )
        )
        savePlaylists(playlists)
    }

    fun deletePlaylist(id: String) {
        val playlists = getAllPlaylists().filter { it.id != id && !it.isDefault }
        savePlaylists(playlists)
    }

    // Vuelve a insertar una playlist (usado al restaurar desde la papelera), preservando su id, canciones y carátula
    fun restorePlaylist(playlist: Playlist) {
        val playlists = getAllPlaylists().toMutableList()
        if (playlists.none { it.id == playlist.id }) {
            playlists.add(playlist)
            savePlaylists(playlists)
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val playlists = getAllPlaylists().map { playlist ->
            if (playlist.id == playlistId && songId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else playlist
        }
        savePlaylists(playlists)
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String) {
        val playlists = getAllPlaylists().map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(customCoverUri = if (coverUri.isBlank()) null else coverUri)
            } else playlist
        }
        savePlaylists(playlists)
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            obj.put("isDefault", playlist.isDefault)
            if (playlist.customCoverUri != null) {
                obj.put("customCoverUri", playlist.customCoverUri)
            }
            val songs = JSONArray()
            playlist.songIds.forEach { songs.put(it) }
            obj.put("songIds", songs)
            array.put(obj)
        }
        prefs.edit().putString("playlists", array.toString()).apply()
    }

    fun importFromM3U(context: Context, uri: Uri): Playlist? {
        return try {
            val lines = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readLines() ?: return null

            val name = lines.firstOrNull { it.startsWith("#PLAYLIST:") }
                ?.removePrefix("#PLAYLIST:") ?: "Playlist Importada"

            val paths = lines.filter { !it.startsWith("#") && it.isNotBlank() }

            val allSongs = loadSongsFromDevice(context)
            val matchedIds = allSongs
                .filter { song -> paths.any { path -> song.path.endsWith(path.trimStart('/')) || song.path == path } }
                .map { it.id }

            val playlist = Playlist(
                id = System.currentTimeMillis().toString(),
                name = name,
                songIds = matchedIds
            )
            val playlists = getAllPlaylists().toMutableList()
            playlists.add(playlist)
            savePlaylists(playlists)
            playlist
        } catch (e: Exception) {
            null
        }
    }

    fun exportToM3U(context: Context, playlist: Playlist): Boolean {
        return try {
            // 1. Obtener todas las canciones del dispositivo para mapear ID -> Path
            val allSongs = loadSongsFromDevice(context)
            val songMap = allSongs.associateBy { it.id }

            // 2. Construir el contenido del archivo M3U
            val stringBuilder = StringBuilder()
            stringBuilder.append("#EXTM3U\n")
            stringBuilder.append("#PLAYLIST:${playlist.name}\n")

            playlist.songIds.forEach { id ->
                val song = songMap[id]
                if (song != null) {
                    // Si el objeto song tiene una propiedad 'title', se puede añadir la metaetiqueta opcional
                    // stringBuilder.append("#EXTINF:-1,${song.title}\n")
                    stringBuilder.append("${song.path}\n")
                }
            }

            // 3. Definir la ruta en la carpeta pública de Descargas (Downloads)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // Sanitizar el nombre del archivo para evitar caracteres ilegales
            val safeName = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(downloadsDir, "$safeName.m3u")

            // 4. Escribir el archivo
            file.writeText(stringBuilder.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}