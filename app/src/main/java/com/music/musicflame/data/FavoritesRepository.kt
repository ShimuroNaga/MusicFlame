package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences

class FavoritesRepository(context: Context) {
    // AHORA: Usa el mismo archivo "settings" que el MusicPlaybackService
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val PREF_FAVORITES_KEY = "favorite_songs_set"

    fun isFavorite(songId: Long): Boolean {
        // Lee la lista de IDs guardados (o un Set vacío si no hay nada)
        val favoritesSet = prefs.getStringSet(PREF_FAVORITES_KEY, setOf()) ?: setOf()
        return favoritesSet.contains(songId.toString())
    }

    fun toggleFavorite(songId: Long) {
        // Para editar un StringSet en SharedPreferences, siempre debemos hacer una copia primero
        val favoritesSet = prefs.getStringSet(PREF_FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val idString = songId.toString()

        if (favoritesSet.contains(idString)) {
            favoritesSet.remove(idString)
        } else {
            favoritesSet.add(idString)
        }

        prefs.edit().putStringSet(PREF_FAVORITES_KEY, favoritesSet).apply()
    }

    // --- NUEVA FUNCIÓN AÑADIDA ---
    fun addFavorite(songId: Long) {
        val favoritesSet = prefs.getStringSet(PREF_FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val idString = songId.toString()

        // Solo la añade si no existe ya
        if (!favoritesSet.contains(idString)) {
            favoritesSet.add(idString)
            prefs.edit().putStringSet(PREF_FAVORITES_KEY, favoritesSet).apply()
        }
    }

    fun removeFavorite(songId: Long) {
        val favoritesSet = prefs.getStringSet(PREF_FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val idString = songId.toString()

        if (favoritesSet.contains(idString)) {
            favoritesSet.remove(idString)
            prefs.edit().putStringSet(PREF_FAVORITES_KEY, favoritesSet).apply()
        }
    }

    fun getAllFavoriteIds(): Set<Long> {
        val favoritesSet = prefs.getStringSet(PREF_FAVORITES_KEY, setOf()) ?: setOf()
        // Convertimos los textos (Strings) de vuelta a números (Longs) de forma segura
        return favoritesSet.mapNotNull { it.toLongOrNull() }.toSet()
    }
}