package com.music.musicflame.widget

import android.content.Context

/**
 * "Buzón" de estado entre el MusicPlaybackService (que vive en 2do plano, incluso
 * con la app cerrada) y el widget de home screen.
 *
 * El servicio escribe aquí cada vez que cambia la canción o el estado de play/pause.
 * El widget (MusicFlameWidgetProvider) y el receiver de acciones (WidgetActionReceiver)
 * solo LEEN de aquí para dibujar el RemoteViews o para saber si hay que pausar/reanudar.
 *
 * Usamos el mismo archivo "settings" que ya usa el resto de la app para no
 * multiplicar archivos de SharedPreferences.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "settings"

    private const val KEY_HAS_SONG = "widget_has_song"
    private const val KEY_TITLE = "widget_song_title"
    private const val KEY_ARTIST = "widget_song_artist"
    private const val KEY_ART_URI = "widget_album_art_uri"
    private const val KEY_IS_PLAYING = "widget_is_playing"
    private const val KEY_MEDIA_ID = "widget_media_id"

    data class WidgetSongState(
        val hasSong: Boolean,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val isPlaying: Boolean,
        val mediaId: String?
    )

    fun save(
        context: Context,
        hasSong: Boolean,
        title: String,
        artist: String,
        albumArtUri: String?,
        isPlaying: Boolean,
        mediaId: String?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAS_SONG, hasSong)
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putString(KEY_ART_URI, albumArtUri)
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putString(KEY_MEDIA_ID, mediaId)
            .apply()
    }

    /** Actualiza solo el flag de reproducción, sin tocar el resto (respuesta instantánea al tocar play/pause). */
    fun updateIsPlaying(context: Context, isPlaying: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .apply()
    }

    fun read(context: Context): WidgetSongState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetSongState(
            hasSong = prefs.getBoolean(KEY_HAS_SONG, false),
            title = prefs.getString(KEY_TITLE, "") ?: "",
            artist = prefs.getString(KEY_ARTIST, "") ?: "",
            albumArtUri = prefs.getString(KEY_ART_URI, null),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            mediaId = prefs.getString(KEY_MEDIA_ID, null)
        )
    }
}
