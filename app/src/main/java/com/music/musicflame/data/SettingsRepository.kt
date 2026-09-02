package com.music.musicflame.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getSystemPrompt(): String = prefs.getString("system_prompt", "") ?: ""
    fun saveSystemPrompt(prompt: String) = prefs.edit().putString("system_prompt", prompt).apply()

    // --- MANEJO DE CANCIONES ---

    fun getDurationFilterMin(): Int = prefs.getInt("duration_filter_min", 0)
    fun saveDurationFilterMin(seconds: Int) = prefs.edit().putInt("duration_filter_min", seconds).apply()

    fun getDurationFilterMax(): Int = prefs.getInt("duration_filter_max", Int.MAX_VALUE)
    fun saveDurationFilterMax(seconds: Int) = prefs.edit().putInt("duration_filter_max", seconds).apply()

    fun getDurationFilterMode(): String = prefs.getString("duration_filter_mode", "only") ?: "only"
    fun saveDurationFilterMode(mode: String) = prefs.edit().putString("duration_filter_mode", mode).apply()

    // --- GUARDAR ETIQUETAS Y CARÁTULA REALES EN EL ARCHIVO (RealTagWriter) ---
    // Si está activado, al editar título/artista/álbum/carátula desde "Editar
    // etiquetas y carátula" también se escribe de verdad en los tags ID3 del
    // archivo .mp3 en disco (no solo en SongCustomizationRepository). Requiere
    // el permiso "Acceso a todos los archivos" (MANAGE_EXTERNAL_STORAGE).
    // Apagado por defecto: modifica archivos reales del usuario, es opt-in.
    fun isRealTagWritingEnabled(): Boolean = prefs.getBoolean("real_tag_writing_enabled", false)
    fun saveRealTagWritingEnabled(enabled: Boolean) = prefs.edit().putBoolean("real_tag_writing_enabled", enabled).apply()

    // --- FORMATOS DE AUDIO A ESCUCHAR (Ajustes > Canciones) ---
    // Conjunto de extensiones (canónicas, ver AudioFormatCatalog) que el
    // usuario decidió OCULTAR de su biblioteca. Un formato ausente de este
    // set se considera visible/activado. loadSongsFromDevice() lo aplica en
    // el punto de carga, así que las pantallas que leen de SongLibraryHolder
    // (SongScreen, AlbumScreen, búsquedas, etc.) nunca ven canciones de un
    // formato oculto. La primera vez que se lee (sin nada guardado todavía)
    // se usa AudioFormatCatalog.DEFAULT_HIDDEN_EXTENSIONS, que hoy solo
    // oculta por defecto los formatos marcados como "no usables" (.aac
    // crudo) — el resto de formatos detectados empieza visible.
    fun getHiddenAudioFormats(): Set<String> {
        val json = prefs.getString("hidden_audio_formats", null) ?: return com.music.musicflame.data.AudioFormatCatalog.DEFAULT_HIDDEN_EXTENSIONS
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
            result
        } catch (e: Exception) {
            com.music.musicflame.data.AudioFormatCatalog.DEFAULT_HIDDEN_EXTENSIONS
        }
    }

    fun saveHiddenAudioFormats(hidden: Set<String>) {
        val array = org.json.JSONArray()
        hidden.forEach { array.put(it) }
        prefs.edit().putString("hidden_audio_formats", array.toString()).apply()
    }

    // --- DESPLEGABLE "PLAYLISTS PREDETERMINADAS" (PlaylistsScreen) ---
    // Recuerda si el usuario dejó expandido o colapsado el grupo de
    // Favoritos/Lo Más Sonado/Por Descubrir, ya que PlaylistsScreen se
    // recompone desde cero cada vez que se vuelve de ver una playlist.
    fun isDefaultPlaylistsExpanded(): Boolean = prefs.getBoolean("default_playlists_expanded", true)
    fun saveDefaultPlaylistsExpanded(expanded: Boolean) = prefs.edit().putBoolean("default_playlists_expanded", expanded).apply()

    // --- APARIENCIA ---
    fun getAppTheme(): String = prefs.getString("app_theme", "Siguiendo al sistema") ?: "Siguiendo al sistema"
    fun saveAppTheme(theme: String) = prefs.edit().putString("app_theme", theme).apply()

    fun saveAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean("amoled_mode", enabled).apply()
    }

    fun isAmoledModeEnabled(): Boolean {
        return prefs.getBoolean("amoled_mode", false)
    }

    fun getUseRoundCorners(): Boolean = prefs.getBoolean("use_round_corners", true)
    fun saveUseRoundCorners(enabled: Boolean) = prefs.edit().putBoolean("use_round_corners", enabled).apply()

    // Opacidad del fondo gris del widget de home screen (0f = transparente, 1f = opaco)
    fun getWidgetBackgroundOpacity(): Float = prefs.getFloat("widget_bg_opacity", 0.8f)
    fun saveWidgetBackgroundOpacity(value: Float) = prefs.edit().putFloat("widget_bg_opacity", value).apply()

    // --- FORMA DE LA CARÁTULA: SQUARE, CIRCLE, HEXAGON, VINYL o SQUIRCLE ---
    fun getAlbumArtShape(): com.music.musicflame.AlbumArtShapeType {
        val name = prefs.getString("album_art_shape", com.music.musicflame.AlbumArtShapeType.SQUARE.name)
            ?: com.music.musicflame.AlbumArtShapeType.SQUARE.name
        return try {
            com.music.musicflame.AlbumArtShapeType.valueOf(name)
        } catch (e: Exception) {
            com.music.musicflame.AlbumArtShapeType.SQUARE
        }
    }
    fun saveAlbumArtShape(shape: com.music.musicflame.AlbumArtShapeType) = prefs.edit().putString("album_art_shape", shape.name).apply()

    fun getCarouselStyle(): String = prefs.getString("carousel_style", "Desactivar") ?: "Desactivar"
    fun saveCarouselStyle(style: String) = prefs.edit().putString("carousel_style", style).apply()

    // --- ICONO DE LA APP ---
    fun getSelectedAppIcon(): String = prefs.getString("selected_app_icon", "default") ?: "default"
    fun saveSelectedAppIcon(key: String) = prefs.edit().putString("selected_app_icon", key).apply()

    // --- COLOR DE TEXTO GLOBAL ---
    // Valores posibles: "Negro" o "Blanco". Controla LocalAppTextColor en toda la app.
    fun getAppTextColor(): String = prefs.getString("app_text_color", "Negro") ?: "Negro"
    fun saveAppTextColor(color: String) = prefs.edit().putString("app_text_color", color).apply()

    // Color de texto personalizado (hex "#RRGGBB"/"#AARRGGBB" o "r,g,b"/"r,g,b,a")
    fun getCustomTextColorHex(): String = prefs.getString("custom_text_color_hex", "#FFFFFF") ?: "#FFFFFF"
    fun saveCustomTextColorHex(value: String) = prefs.edit().putString("custom_text_color_hex", value).apply()

    // --- TIPO DE LETRA GLOBAL (catálogo, ideas de fuentes) ---
    // Guarda el AppFont.id (ver ui/theme/AppFonts.kt), aplicado a TODA la app
    // (no solo al título de la canción) vía MusicFlameTheme. Se guarda como
    // String plano (no el enum) para no acoplar este repo a la capa de UI,
    // mismo criterio que el resto de las claves de este archivo.
    fun getAppFont(): String = prefs.getString("app_font", "roboto") ?: "roboto"
    fun saveAppFont(id: String) = prefs.edit().putString("app_font", id).apply()

    // --- REPRODUCCIÓN Y CUENTA ---
    fun getPlayInBackground(): Boolean = prefs.getBoolean("play_in_background", true)
    fun savePlayInBackground(enabled: Boolean) = prefs.edit().putBoolean("play_in_background", enabled).apply()

    // Pausar automáticamente al desconectar Bluetooth/auriculares
    fun getPauseOnDisconnect(): Boolean = prefs.getBoolean("pause_on_disconnect", true)
    fun savePauseOnDisconnect(enabled: Boolean) = prefs.edit().putBoolean("pause_on_disconnect", enabled).apply()

    // --- ECUALIZADOR Y AUDIO PRO ---
    fun getEqPresetSelected(): String = prefs.getString("eq_preset_selected", "Flat") ?: "Flat"
    fun saveEqPresetSelected(preset: String) = prefs.edit().putString("eq_preset_selected", preset).apply()

    fun getEqBand(index: Int): Float = prefs.getFloat("eq_band_$index", 0.5f)
    fun saveEqBand(index: Int, value: Float) = prefs.edit().putFloat("eq_band_$index", value).apply()

    fun getBassBoost(): Float = prefs.getFloat("bass_boost", 20f)
    fun saveBassBoost(value: Float) = prefs.edit().putFloat("bass_boost", value).apply()

    fun getVirtualizer(): Float = prefs.getFloat("virtualizer", 10f)
    fun saveVirtualizer(value: Float) = prefs.edit().putFloat("virtualizer", value).apply()

    fun getEqVolume(): Float = prefs.getFloat("eq_volume", 80f)
    fun saveEqVolume(value: Float) = prefs.edit().putFloat("eq_volume", value).apply()

    // Nuevos Efectos Pro
    fun getLoudnessEnhancer(): Float = prefs.getFloat("loudness_enhancer", 0f)
    fun saveLoudnessEnhancer(value: Float) = prefs.edit().putFloat("loudness_enhancer", value).apply()

    fun getReverbPreset(): Int = prefs.getInt("reverb_preset", 0)
    fun saveReverbPreset(value: Int) = prefs.edit().putInt("reverb_preset", value).apply()

    // --- IMAGEN DE FONDO ---
    fun getBackgroundImageUri(): String? = prefs.getString("background_image_uri", null)
    fun saveBackgroundImageUri(uri: String) = prefs.edit().putString("background_image_uri", uri).apply()
    fun removeBackgroundImage() = prefs.edit().remove("background_image_uri").apply()

    fun saveBackgroundBrightness(value: Float) = prefs.edit().putFloat("bg_brightness", value).apply()
    fun getBackgroundBrightness(): Float = prefs.getFloat("bg_brightness", 0f)

    // Cantidad de carátulas por renglón en la pantalla de Álbumes.
    // 2 = carátulas grandes (default), 4 = carátulas chicas (más por renglón).
    fun saveAlbumGridColumns(columns: Int) = prefs.edit().putInt("album_grid_columns", columns).apply()
    fun getAlbumGridColumns(): Int = prefs.getInt("album_grid_columns", 2)

    // Cantidad de barras del ecualizador gráfico animado del reproductor a pantalla
    // completa. 32 = estándar (default), 6 = mínimo, 64 = máximo.
    fun saveEqualizerBarCount(count: Int) = prefs.edit().putInt("equalizer_bar_count", count).apply()
    fun getEqualizerBarCount(): Int = prefs.getInt("equalizer_bar_count", 32)

    fun getPlayerGifUri(): String? = prefs.getString("player_gif_uri", null)
    fun savePlayerGifUri(uri: String) = prefs.edit().putString("player_gif_uri", uri).apply()
    fun removePlayerGifUri() = prefs.edit().remove("player_gif_uri").apply()

    // --- ESTILO DE ECUALIZADOR GRÁFICO (catálogo de personalizaciones estéticas) ---
    // Estilo elegido en Ajustes > Apariencia > "Estilo de ecualizador gráfico":
    // barras clásicas, doble espejado, ondas de agua, círculo pulsante,
    // partículas, barras finas o VU meter retro. Ver EqualizerStyle.kt.
    fun getEqualizerStyle(): com.music.musicflame.ui.components.EqualizerStyle {
        val name = prefs.getString("equalizer_style", com.music.musicflame.ui.components.EqualizerStyle.BARS.name)
        return com.music.musicflame.ui.components.EqualizerStyle.fromNameOrDefault(name)
    }
    fun saveEqualizerStyle(style: com.music.musicflame.ui.components.EqualizerStyle) =
        prefs.edit().putString("equalizer_style", style.name).apply()

    // --- COLOR PROPIO DEL ECUALIZADOR GRÁFICO (punto 1 del catálogo) ---
    // Modo "Adaptativo" (default, sin cambios de comportamiento): blanco o
    // negro según luminancia del fondo/imagen, tal cual funcionaba antes.
    // Modo "Personalizado": usa equalizer_custom_color_hex, mismo formato
    // (#RRGGBB o r,g,b,a) que ya soporta parseCustomTextColor. Ortogonal al
    // estilo (BARS, MIRRORED_BARS, etc.) — aplica sea cual sea el estilo
    // elegido arriba.
    fun getEqualizerColorMode(): String = prefs.getString("equalizer_color_mode", "Adaptativo") ?: "Adaptativo"
    fun saveEqualizerColorMode(mode: String) = prefs.edit().putString("equalizer_color_mode", mode).apply()

    fun getEqualizerCustomColorHex(): String = prefs.getString("equalizer_custom_color_hex", "#FFFFFF") ?: "#FFFFFF"
    fun saveEqualizerCustomColorHex(value: String) = prefs.edit().putString("equalizer_custom_color_hex", value).apply()

    // --- COLOR ÚNICO DEL "NOW PLAYING" INDICATOR (punto 3 del catálogo) ---
    // Mismo patrón que el color del ecualizador (punto 1): "Adaptativo" (default,
    // blanco/negro según luminancia del fondo, tal cual funcionaba antes) o
    // "Personalizado" (now_playing_custom_color_hex, mismo formato #RRGGBB o
    // r,g,b,a). Aplica al indicador animado de "reproduciendo ahora" en listas
    // de canciones, cola y playlists (NowPlayingIndicator).
    fun getNowPlayingColorMode(): String = prefs.getString("now_playing_color_mode", "Adaptativo") ?: "Adaptativo"
    fun saveNowPlayingColorMode(mode: String) = prefs.edit().putString("now_playing_color_mode", mode).apply()

    fun getNowPlayingCustomColorHex(): String = prefs.getString("now_playing_custom_color_hex", "#FFFFFF") ?: "#FFFFFF"
    fun saveNowPlayingCustomColorHex(value: String) = prefs.edit().putString("now_playing_custom_color_hex", value).apply()

    companion object {
        // NOTA PARA SESIÓN POSTERIOR: este catálogo (estilos de ecualizador, y
        // en el futuro colores custom del ecualizador/letra/now-playing y el
        // widget de disco giratorio) todavía NO está atado a
        // LicenseRepository.isProUnlocked(). Por ahora queda libre para poder
        // probar todo; cuando se decida cómo se va a vender, esta constante es
        // el único lugar que hay que tocar para "cerrar la llave" de nuevo
        // (reemplazar `true` por `licenseRepository.isProUnlocked()` donde se
        // use, o convertirla en función).
        const val EQUALIZER_STYLES_UNLOCKED_FOR_TESTING = true
    }

    // --- PERSISTENCIA DEL MIX DIARIO ---
    fun getLastMixDate(): String {
        return prefs.getString("last_mix_date", "") ?: ""
    }

    fun saveLastMixDate(date: String) {
        prefs.edit().putString("last_mix_date", date).apply()
    }

    fun getMixSongs(): List<Long> {
        val json = prefs.getString("mix_songs", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<Long>()
            for (i in 0 until array.length()) {
                result.add(array.getLong(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMixSongs(songIds: List<Long>) {
        val array = org.json.JSONArray()
        songIds.forEach { array.put(it) }
        prefs.edit().putString("mix_songs", array.toString()).apply()
    }

    // --- LYRICS (letra sincronizada) ---
    // Velocidad de la animación entre líneas: 0.5 (lenta) a 2.0 (rápida). 1.0 = normal.
    fun getLyricsSpeed(): Float = prefs.getFloat("lyrics_speed", 1.0f)
    fun saveLyricsSpeed(value: Float) = prefs.edit().putFloat("lyrics_speed", value).apply()

    // Tipo de animación entre líneas: "Deslizar", "Desvanecer" o "Rebote".
    fun getLyricsAnimationType(): String = prefs.getString("lyrics_animation_type", "Deslizar") ?: "Deslizar"
    fun saveLyricsAnimationType(type: String) = prefs.edit().putString("lyrics_animation_type", type).apply()

    // Color del texto de la letra: "Adaptativo" (Material You), "Blanco", "Negro" o "Personalizado".
    fun getLyricsTextColorMode(): String = prefs.getString("lyrics_text_color_mode", "Adaptativo") ?: "Adaptativo"
    fun saveLyricsTextColorMode(mode: String) = prefs.edit().putString("lyrics_text_color_mode", mode).apply()

    fun getLyricsCustomColorHex(): String = prefs.getString("lyrics_custom_color_hex", "#FFFFFF") ?: "#FFFFFF"
    fun saveLyricsCustomColorHex(value: String) = prefs.edit().putString("lyrics_custom_color_hex", value).apply()

    // Letra en vivo dentro del widget de home screen: reemplaza la línea de
    // artista por la línea de letra sincronizada activa mientras suena la
    // canción. Activado por defecto; el usuario puede apagarlo si prefiere
    // ver siempre el nombre del artista en el widget.
    fun isLyricsInWidgetEnabled(): Boolean = prefs.getBoolean("lyrics_in_widget_enabled", true)
    fun saveLyricsInWidgetEnabled(enabled: Boolean) = prefs.edit().putBoolean("lyrics_in_widget_enabled", enabled).apply()

    // Widget cuadrado de letra completa (180x180dp): toggle SEPARADO del de
    // arriba. Uno controla si hay letra en el widget en general; este controla
    // si además se ofrece la variante cuadrada sin recorte. Por defecto
    // desactivado (opt-in), a diferencia del anterior.
    fun isFullLyricsSquareWidgetEnabled(): Boolean = prefs.getBoolean("full_lyrics_square_widget_enabled", false)
    fun saveFullLyricsSquareWidgetEnabled(enabled: Boolean) = prefs.edit().putBoolean("full_lyrics_square_widget_enabled", enabled).apply()

    // --- ONBOARDING DE PRIMER USO ---
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("onboarding_completed", false)
    fun setOnboardingCompleted(completed: Boolean) = prefs.edit().putBoolean("onboarding_completed", completed).apply()
}