package com.music.musicflame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashedSong(
    val song: Song,
    val deletedAt: Long
)

class TrashRepository(context: Context) {
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