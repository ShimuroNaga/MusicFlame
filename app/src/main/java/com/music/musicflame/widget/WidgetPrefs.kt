package com.music.musicflame.widget

import android.content.Context
import org.json.JSONArray

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

    // Letra en vivo: hasta 3 líneas (la activa + hasta 2 siguientes), guardadas
    // como JSON aparte del resto del estado para no tocar la firma de save()
    // (que llaman WidgetActionReceiver y MusicPlaybackService en varios puntos
    // que no saben nada de letras). Se atan al mediaId para que un refresco
    // tardío de una canción que ya se dejó de escuchar no pise las líneas de
    // la canción nueva.
    private const val KEY_LYRICS_LINES = "widget_lyrics_lines"
    private const val KEY_LYRICS_MEDIA_ID = "widget_lyrics_media_id"

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

    /**
     * Guarda hasta 3 líneas de letra (la activa primero) para [mediaId]. Se
     * llama muy seguido mientras suena una canción con letra sincronizada
     * (cada vez que cambia la línea activa), así que se mantiene deliberadamente
     * liviano: solo estas 2 claves, sin tocar el resto del estado del widget.
     */
    fun saveLyricsLines(context: Context, mediaId: String?, lines: List<String>) {
        val array = JSONArray()
        lines.take(3).forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LYRICS_LINES, array.toString())
            .putString(KEY_LYRICS_MEDIA_ID, mediaId)
            .apply()
    }

    /** Borra las líneas de letra guardadas (canción sin letra, o letra desactivada en Ajustes). */
    fun clearLyricsLines(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LYRICS_LINES)
            .remove(KEY_LYRICS_MEDIA_ID)
            .apply()
    }

    /**
     * Lee las líneas de letra guardadas, PERO solo si siguen siendo de
     * [currentMediaId]. Esto evita el caso borde de un refresco en vuelo desde
     * la canción anterior pisando el widget justo después del cambio de canción.
     */
    fun readLyricsLines(context: Context, currentMediaId: String?): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMediaId = prefs.getString(KEY_LYRICS_MEDIA_ID, null)
        if (currentMediaId == null || savedMediaId != currentMediaId) return emptyList()
        val json = prefs.getString(KEY_LYRICS_LINES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
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
