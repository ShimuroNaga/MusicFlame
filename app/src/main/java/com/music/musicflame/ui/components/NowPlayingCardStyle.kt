package com.music.musicflame.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.music.musicflame.ui.theme.LocalNowPlayingIndicatorColor

/**
 * Colores de la card de la canción que está sonando ahora mismo.
 *
 * Reemplaza al antiguo indicador animado de barritas: ahora es la CARD completa
 * la que se "activa" con el color del "Now Playing" (Ajustes > Apariencia >
 * Color del "Now Playing": Adaptativo / Personalizado / Arcoíris). Así sigue
 * exactamente la misma regla de color de antes.
 *
 * [container] es el fondo de la card y [content] el color de título/artista/íconos,
 * elegido en blanco o negro según la luminancia del color para que SIEMPRE se lea
 * (Adaptativo = blanco sobre fondo oscuro / negro sobre fondo claro; con Arcoíris el
 * texto se reajusta solo conforme cambia el color).
 *
 * IMPORTANTE (rendimiento): llamar a esta función SOLO para la card que suena
 * (ej. `if (isPlaying) nowPlayingCardColors() else null`). Así, con Arcoíris,
 * únicamente esa card se recompone en cada tick y no toda la lista.
 */
@Immutable
data class NowPlayingCardColors(val container: Color, val content: Color)

@Composable
fun nowPlayingCardColors(): NowPlayingCardColors {
    val base = LocalNowPlayingIndicatorColor.current
    val content = if (base.luminance() > 0.5f) Color.Black else Color.White
    return NowPlayingCardColors(container = base.copy(alpha = 0.9f), content = content)
}
