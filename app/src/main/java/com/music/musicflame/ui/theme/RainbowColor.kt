package com.music.musicflame.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color

/**
 * Modo de color "Arcoíris" (catálogo, punto 6): en vez de un color fijo
 * (Adaptativo/Personalizado), el color va recorriendo todo el espectro de
 * forma continua, como el RGB de un periférico gamer. Es el mismo concepto
 * en las 4 opciones que lo soportan: color del ecualizador, color de texto
 * general, color del "Now Playing" y color de texto de Lyrics.
 *
 * Grados de tono (hue) que avanza por segundo. 360/RAINBOW_DEGREES_PER_SECOND
 * = segundos que tarda una vuelta completa del espectro (acá, 6s).
 */
private const val RAINBOW_DEGREES_PER_SECOND = 60f
private val RAINBOW_CYCLE_MS = (360_000f / RAINBOW_DEGREES_PER_SECOND).toInt()

/** Nombre del modo, tal cual se guarda en SettingsRepository (mismo patrón de
 * strings crudos que "Adaptativo"/"Personalizado"/"Negro"/"Blanco"). */
const val COLOR_MODE_RAINBOW = "Arcoiris"

/**
 * Fase animada (0..360°) del modo Arcoíris, en loop infinito y lineal.
 * Cualquier composable que necesite pintar con Arcoíris debe llamar a esto Y
 * usar [rainbowColorAt] con el valor devuelto — así, si varios elementos
 * (ecualizador, texto, lyrics, now playing) están en Arcoíris a la vez, cada
 * uno corre su propia animación pero todas van a la misma velocidad y
 * dirección, por lo que se sienten parte del mismo efecto.
 */
@Composable
fun rememberRainbowPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "rainbow_phase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RAINBOW_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow_hue"
    )
}

/**
 * Color arcoíris en un instante/posición dado.
 *
 * @param phaseDeg fase animada global, de [rememberRainbowPhase].
 * @param spreadFraction posición (0f..1f) del elemento dentro de su fila o
 *   grupo (por ejemplo, índice de barra / cantidad total de barras del
 *   ecualizador). En 0f, el elemento simplemente cicla de color con el
 *   tiempo; valores > 0f además reparten el arcoíris en el espacio, para que
 *   elementos vecinos tengan tonos distintos entre sí en un mismo instante
 *   (ej.: cada barra del ecualizador con un color distinto, moviéndose).
 * @param alpha transparencia a aplicar sobre el color resultante.
 */
fun rainbowColorAt(phaseDeg: Float, spreadFraction: Float = 0f, alpha: Float = 1f): Color {
    val hue = ((phaseDeg + spreadFraction * 360f) % 360f + 360f) % 360f
    return Color.hsv(hue, 0.85f, 1f, alpha.coerceIn(0f, 1f))
}
