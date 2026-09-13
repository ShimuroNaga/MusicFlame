package com.music.musicflame.data

import android.content.Context

/**
 * Registro persistente y liviano de "desde qué playlist se está reproduciendo
 * ahora mismo", para que componentes que viven fuera de la Activity (como el
 * Quick Settings Tile de Mezclar) sepan si deben remezclar esa playlist en
 * vez de solo activar el modo aleatorio normal de Media3.
 *
 * Se guarda en SharedPreferences (no en memoria) a propósito: el Tile puede
 * ejecutarse en un momento en que la Activity ya no existe, mientras el
 * servicio de reproducción sigue vivo en segundo plano.
 */
object PlaybackContextTracker {
    private const val PREFS_NAME = "playback_context"
    private const val KEY_PLAYLIST_ID = "active_playlist_id"
    private const val KEY_PLAYLIST_KIND = "active_playlist_kind"

    fun setActivePlaylist(context: Context, playlistId: String, kind: PlaylistKind) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAYLIST_ID, playlistId)
            .putString(KEY_PLAYLIST_KIND, kind.name)
            .apply()
    }

    fun clearActivePlaylist(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_PLAYLIST_ID)
            .remove(KEY_PLAYLIST_KIND)
            .apply()
    }

    /** null si el usuario no está reproduciendo desde una playlist en este momento. */
    fun getActivePlaylist(context: Context): Pair<String, PlaylistKind>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_PLAYLIST_ID, null) ?: return null
        val kindName = prefs.getString(KEY_PLAYLIST_KIND, null) ?: return null
        val kind = try {
            PlaylistKind.valueOf(kindName)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return id to kind
    }
}
