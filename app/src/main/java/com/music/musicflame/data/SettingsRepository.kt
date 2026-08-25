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

    // --- ONBOARDING DE PRIMER USO ---
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("onboarding_completed", false)
    fun setOnboardingCompleted(completed: Boolean) = prefs.edit().putBoolean("onboarding_completed", completed).apply()
}