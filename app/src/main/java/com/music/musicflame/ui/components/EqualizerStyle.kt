package com.music.musicflame.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.music.musicflame.R
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
 * catalogId: id en PaymentCatalog.ITEMS que hay que consultar con
 *            LicenseRepository.isItemUnlocked/ProStatusHolder.isItemUnlocked
 *            para saber si este estilo está desbloqueado; null = gratis
 *            (BARS es el único estilo gratis).
 */
enum class EqualizerStyle(@StringRes val displayNameRes: Int, @StringRes val descriptionRes: Int, val catalogId: String?) {
    BARS(
        R.string.eqs_bars,
        R.string.eqs_d_bars,
        catalogId = null
    ),
    MIRRORED_BARS(
        R.string.pay_it_mirrored,
        R.string.eqs_d_mirrored,
        catalogId = "eq_style_mirrored"
    ),
    WATER_WAVE(
        R.string.pay_it_wave,
        R.string.eqs_d_wave,
        catalogId = "eq_style_wave"
    ),
    PULSE_CIRCLE(
        R.string.pay_it_pulse,
        R.string.eqs_d_pulse,
        catalogId = "eq_style_pulse"
    ),
    PARTICLES(
        R.string.pay_it_particles,
        R.string.eqs_d_particles,
        catalogId = "eq_style_particles"
    ),
    THIN_BARS(
        R.string.pay_it_thin,
        R.string.eqs_d_thin,
        catalogId = "eq_style_thin"
    ),
    VU_METER_RETRO(
        R.string.pay_it_vu,
        R.string.eqs_d_vu,
        catalogId = "eq_style_vu"
    ),
    OSCILLOSCOPE(
        R.string.pay_it_osc,
        R.string.eqs_d_osc,
        catalogId = "eq_style_oscilloscope"
    ),
    CONCENTRIC_RIPPLES(
        R.string.pay_it_skyline,
        R.string.eqs_d_ripples,
        catalogId = "eq_style_skyline"
    ),
    CONSTELLATION(
        R.string.pay_it_rain,
        R.string.eqs_d_constellation,
        catalogId = "eq_style_rain"
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
