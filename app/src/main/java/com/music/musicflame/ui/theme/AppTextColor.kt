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