package com.music.musicflame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.max

// Cuántas barras se dibujan por defecto.
private const val BAR_COUNT = 32

/**
 * Barras de espectro tipo ecualizador clásico, en un solo [color] sólido.
 *
 * NOTA (post-refactor): toda la captura/FFT/auto-gain/detección de golpe que
 * antes vivía ACÁ ADENTRO ahora está en AudioSpectrumEngine.kt
 * (ver [rememberAudioSpectrum]), compartida por todos los estilos de
 * ecualizador nuevos (espejado, ondas, círculo, partículas, etc. — ver
 * EqualizerStyleRenderers.kt). Este composable se mantiene tal cual estaba
 * (mismo nombre, mismos parámetros) para no romper a quien ya lo usaba;
 * internamente ahora es un simple wrapper de "motor + dibujo de barras".
 * Si estás agregando la app nueva funcionalidad de estilos seleccionables,
 * usá [GraphicEqualizer] en vez de este, que ya elige el estilo guardado
 * en Ajustes.
 */
@Composable
fun AudioVisualizerBars(
    audioSessionId: Int,
    isPlaying: Boolean,
    hasRecordAudioPermission: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = BAR_COUNT
) {
    val spectrum = rememberAudioSpectrum(audioSessionId, isPlaying, hasRecordAudioPermission, barCount)
    BarsEqualizerCanvas(spectrum = spectrum, color = color, modifier = modifier)
}

/**
 * Dibujo puro de barras clásicas a partir de un [EqualizerLevelsState] ya
 * calculado (sea real, del audio en vivo, o falso/animado para la vista
 * previa del selector de estilo en Ajustes). Extraído para que
 * [AudioVisualizerBars] y [GraphicEqualizer] (estilo BARS) compartan
 * exactamente el mismo dibujo.
 */
@Composable
fun BarsEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height
        val minHeightFraction = 0.015f

        for (index in 0 until barCount) {
            val level = spectrum.displayLevels[index].coerceIn(0f, 1f)
            val barHeight = maxBarHeight * max(level, minHeightFraction)
            val x = index * (barWidth + spacing)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, maxBarHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
