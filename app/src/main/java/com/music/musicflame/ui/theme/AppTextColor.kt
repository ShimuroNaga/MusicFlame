package com.music.musicflame.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color de texto global de la app, controlado por el usuario desde Ajustes
 * (Negro o Blanco). Se provee una sola vez en MusicFlameTheme y cualquier
 * pantalla/card puede leerlo con LocalAppTextColor.current, sin necesidad
 * de pasarlo como parámetro entre composables.
 */
val LocalAppTextColor = compositionLocalOf { Color.Black }

/**
 * Color único del indicador animado de "reproduciendo ahora" (punto 3 del
 * catálogo, NowPlayingIndicator). Se resuelve una sola vez en MusicFlameTheme
 * (modo "Adaptativo" = blanco/negro según luminancia real del fondo, igual que
 * el default histórico del componente; "Personalizado" = el hex elegido en
 * Ajustes > Apariencia > "Color del 'Now Playing'") y cualquier lista de
 * canciones/cola/playlist lo lee de acá en vez de recibir SettingsRepository.
 * El valor por defecto de abajo nunca se usa en la app real: MusicFlameTheme
 * SIEMPRE lo sobreescribe desde la raíz, igual que con LocalAppTextColor.
 */
val LocalNowPlayingIndicatorColor = compositionLocalOf { Color.White }

/**
 * Convierte el texto guardado por el usuario ("#RRGGBB", "#AARRGGBB" o "r,g,b"/"r,g,b,a")
 * en un Color real. Si el formato es inválido, cae de vuelta a negro para no romper la UI.
 */
fun parseCustomTextColor(input: String): Color {
    val trimmed = input.trim()
    return try {
        if (trimmed.contains(",")) {
            val parts = trimmed.split(",").map { it.trim().toInt().coerceIn(0, 255) }
            when (parts.size) {
                3 -> Color(parts[0], parts[1], parts[2])
                4 -> Color(parts[0], parts[1], parts[2], parts[3])
                else -> Color.Black
            }
        } else {
            val hex = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            Color(android.graphics.Color.parseColor(hex))
        }
    } catch (e: Exception) {
        Color.Black
    }
}