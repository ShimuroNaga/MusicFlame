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