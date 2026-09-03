package com.music.musicflame.ui.theme

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
private const val RAINBOW_CYCLE_MS = (360_000f / RAINBOW_DEGREES_PER_SECOND).toLong()

// Cuántas veces por segundo se actualiza el color mientras Arcoiris está
// activo. Antes esto se movía a 60 veces por segundo (un frame de
// animación = una recomposición de CADA Text que use el color). El ojo no
// distingue 60 pasos por segundo de, digamos, 18-20 en un degradado de
// color que tarda 6s en dar la vuelta completa — así que bajarlo a esto
// se ve prácticamente idéntico y recompone ~70% menos seguido mientras el
// modo está prendido.
private const val RAINBOW_UPDATES_PER_SECOND = 18
private const val RAINBOW_TICK_MS = 1000L / RAINBOW_UPDATES_PER_SECOND

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
 *
 * Se calcula por tiempo real transcurrido (SystemClock.elapsedRealtime), no
 * por cantidad de frames dibujados, así que la velocidad de la vuelta
 * completa (6s) no cambia aunque se actualice menos seguido que antes.
 */
@Composable
fun rememberRainbowPhase(): State<Float> {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime() - start
            phase.value = (elapsed % RAINBOW_CYCLE_MS).toFloat() / RAINBOW_CYCLE_MS * 360f
            delay(RAINBOW_TICK_MS)
        }
    }
    return phase
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
