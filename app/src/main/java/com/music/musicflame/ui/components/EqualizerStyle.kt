package com.music.musicflame.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Catálogo de estilos visuales del ecualizador gráfico. Es una preferencia
 * ORTOGONAL al color (ver getEqualizerColorMode/getEqualizerCustomColor en
 * SettingsRepository): cualquier estilo se puede combinar con cualquier color.
 *
 * displayName: lo que ve el usuario en el selector de Ajustes.
 * description: subtítulo corto que explica el estilo en una línea.
 */
enum class EqualizerStyle(val displayName: String, val description: String) {
    BARS(
        "Barras clásicas",
        "El ecualizador de siempre: barras verticales tipo espectro."
    ),
    MIRRORED_BARS(
        "Doble espejado",
        "Dos filas de barras reflejadas, arriba y abajo, simétricas."
    ),
    WATER_WAVE(
        "Ondas de agua",
        "Una onda continua y fluida, tipo osciloscopio."
    ),
    PULSE_CIRCLE(
        "Círculo pulsante",
        "Un aro que late de tamaño según la intensidad del audio."
    ),
    PARTICLES(
        "Partículas",
        "Puntos que saltan y rebotan con cada frecuencia."
    ),
    THIN_BARS(
        "Barras finas",
        "Versión ultra delgada, estilo Spotify Canvas."
    ),
    VU_METER_RETRO(
        "VU meter retro",
        "Agujas analógicas estilo ecualizador vintage."
    );

    companion object {
        private val map = entries.associateBy { it.name }

        /** Devuelve [BARS] si [name] no corresponde a ningún estilo conocido. */
        fun fromNameOrDefault(name: String?): EqualizerStyle =
            map[name] ?: BARS
    }
}

/**
 * Genera un [EqualizerLevelsState] FALSO (no engancha ningún Visualizer real):
 * cada barra oscila con una combinación de senos con fase distinta, más un
 * "pulso" superpuesto, para que la vista previa animada del selector de estilo
 * en Ajustes se vea viva y variada sin necesitar que haya música sonando.
 *
 * Usa el MISMO [EqualizerLevelsState] (y por lo tanto el mismo código de
 * dibujo de EqualizerStyleRenderers.kt) que el ecualizador real — la única
 * diferencia es de dónde viene el número, nunca cómo se dibuja.
 */
@Composable
fun rememberFakeEqualizerLevels(barCount: Int = 20): EqualizerLevelsState {
    val state = remember(barCount) { EqualizerLevelsState(barCount) }

    LaunchedEffect(barCount) {
        var t = 0f
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastNanos == 0L) 0f else (frameNanos - lastNanos) / 1_000_000_000f
                lastNanos = frameNanos
                t += dt
                for (i in 0 until barCount) {
                    val phase = i * 0.7f
                    val base = 0.5f + 0.5f * sin(t * 2.1f + phase)
                    val pulse = 0.5f + 0.5f * sin(t * 5.4f + phase * 1.3f)
                    val mixed = (base * 0.55f + pulse * 0.45f).coerceIn(0f, 1f)
                    state.displayLevels[i] = mixed.pow(0.85f)
                }
            }
            state.tick++
        }
    }

    return state
}
