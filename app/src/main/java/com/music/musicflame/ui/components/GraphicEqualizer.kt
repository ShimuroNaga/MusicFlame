package com.music.musicflame.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Ecualizador gráfico enganchado al audio REAL, con el [style] elegido por el
 * usuario en Ajustes > Apariencia > "Estilo de ecualizador gráfico". Es el
 * reemplazo directo de usar AudioVisualizerBars a mano: mismos parámetros de
 * audio, más el estilo como una opción extra.
 *
 * El [color] sigue siendo una preferencia totalmente aparte (manual o
 * extraído de la carátula, ver el resto del catálogo de personalizaciones) —
 * este composable no decide de dónde sale el color, solo lo aplica al estilo
 * elegido.
 *
 * NOTA: por ahora estos estilos están libres para probar (no atados a
 * LicenseRepository.isProUnlocked todavía, ver EQUALIZER_STYLES_UNLOCKED_FOR_TESTING
 * en SettingsRepository). Eso se conecta en una sesión posterior.
 */
@Composable
fun GraphicEqualizer(
    style: EqualizerStyle,
    audioSessionId: Int,
    isPlaying: Boolean,
    hasRecordAudioPermission: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    // Algunos estilos se ven mejor con una cantidad de "barras" (puntos de
    // muestreo del espectro) distinta a la clásica, aunque el usuario haya
    // dejado el slider de Ajustes en el valor por defecto:
    //  - Partículas: con 64 puntos se amontonan demasiado: se limita a 40 como
    //    techo para que cada partícula tenga su propio espacio.
    //  - El resto de los estilos reutiliza tal cual el barCount configurado.
    val effectiveBarCount = when (style) {
        EqualizerStyle.PARTICLES -> barCount.coerceAtMost(40)
        else -> barCount
    }

    val spectrum = rememberAudioSpectrum(
        audioSessionId = audioSessionId,
        isPlaying = isPlaying,
        hasRecordAudioPermission = hasRecordAudioPermission,
        barCount = effectiveBarCount
    )

    EqualizerCanvas(style = style, spectrum = spectrum, color = color, modifier = modifier)
}
