package com.music.musicflame.audio

/**
 * Presets extra del ecualizador. Una sola fuente de verdad para los dos EQ de la app:
 *
 *  - EQ gratis (Studio Pro EQ en SettingsScreen.kt, Equalizer nativo de Android, 5 bandas
 *    60/230/910/3600/14000 Hz): valores normalizados -1..1 (1 = máximo de la banda del
 *    dispositivo, igual que los presets que ya existían: Flat/Rock/Pop/etc.).
 *  - EQ Pro (ProEqualizerDialog.kt, motor biquad de 10 bandas
 *    31/62/125/250/500/1k/2k/4k/8k/16k Hz): ganancia en dB, dentro de
 *    ProBiquadEqualizerAudioProcessor.MIN/MAX_BAND_GAIN_DB (±15).
 *
 * No incluye los que ya existían: "Flat", "Pop", "Rock" y "Classical" (= Classic) ya están en el
 * EQ gratis. En el EQ Pro ya existía solo "Flat", así que ahí Pop/Rock/Classic sí se agregan.
 */
object EqPresets {

    /** Preset del EQ gratis: 5 bandas (-1..1) + graves nativos opcionales (0..100, null = no tocarlos). */
    class FreePreset(val bands: List<Float>, val bassBoost: Float? = null)

    // Orden en el que aparecen los chips (después de los que ya existían, antes de "Customizar").
    val FREE: List<Pair<String, FreePreset>> = listOf(
        "Bass" to FreePreset(listOf(0.6f, 0.3f, 0f, 0f, 0f), bassBoost = 60f),
        "Mids" to FreePreset(listOf(0f, 0.3f, 0.6f, 0.3f, 0f)),
        "Treble" to FreePreset(listOf(0f, 0f, 0f, 0.3f, 0.6f)),
        "Bass and treble" to FreePreset(listOf(0.6f, 0.3f, -0.1f, 0.3f, 0.6f), bassBoost = 60f),
        "Full bass" to FreePreset(listOf(1f, 0.8f, 0f, 0f, 0f), bassBoost = 100f),
        "Full mids" to FreePreset(listOf(0f, 0.7f, 1f, 0.7f, 0f)),
        "Full treble" to FreePreset(listOf(0f, 0f, 0f, 0.8f, 1f)),
        "Reduce bass" to FreePreset(listOf(-0.5f, -0.25f, 0f, 0f, 0f), bassBoost = 0f),
        "Reduce mids" to FreePreset(listOf(0f, -0.3f, -0.5f, -0.3f, 0f)),
        "Reduce treble" to FreePreset(listOf(0f, 0f, 0f, -0.25f, -0.5f)),
        "No bass" to FreePreset(listOf(-1f, -1f, 0f, 0f, 0f), bassBoost = 0f),
        "No mids" to FreePreset(listOf(0f, -0.8f, -1f, -0.8f, 0f)),
        "No treble" to FreePreset(listOf(0f, 0f, 0f, -1f, -1f)),
        "Dance" to FreePreset(listOf(0.7f, 0.5f, 0f, 0.3f, 0.4f), bassBoost = 50f),
        "Live" to FreePreset(listOf(-0.3f, 0f, 0.3f, 0.4f, 0.3f)),
        "Speaker" to FreePreset(listOf(-0.4f, 0.1f, 0.3f, 0.3f, 0f)),
        "Soft" to FreePreset(listOf(0.2f, 0.1f, 0f, -0.2f, -0.4f)),
        "Techno" to FreePreset(listOf(0.8f, 0.5f, -0.3f, 0.4f, 0.5f), bassBoost = 50f)
    )

    val FREE_NAMES: List<String> = FREE.map { it.first }
    val FREE_BANDS: Map<String, List<Float>> = FREE.associate { it.first to it.second.bands }
    val FREE_BASS: Map<String, Float> =
        FREE.mapNotNull { (name, p) -> p.bassBoost?.let { name to it } }.toMap()

    /** Presets del EQ Pro: 10 bandas en dB (31 Hz ... 16 kHz). */
    val PRO: List<Pair<String, FloatArray>> = listOf(
        "Bass" to floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Mids" to floatArrayOf(0f, 0f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, 0f),
        "Treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 6f, 6f),
        "Bass and treble" to floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 2f, 5f, 6f),
        "Full bass" to floatArrayOf(12f, 11f, 9f, 5f, 1f, 0f, 0f, 0f, 0f, 0f),
        "Full mids" to floatArrayOf(0f, 0f, 1f, 5f, 9f, 10f, 9f, 5f, 1f, 0f),
        "Full treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 4f, 8f, 11f, 12f),
        "Reduce bass" to floatArrayOf(-6f, -5f, -4f, -2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Reduce mids" to floatArrayOf(0f, 0f, 0f, -2f, -4f, -5f, -4f, -2f, 0f, 0f),
        "Reduce treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -4f, -6f, -6f),
        "No bass" to floatArrayOf(-15f, -15f, -13f, -8f, -2f, 0f, 0f, 0f, 0f, 0f),
        "No mids" to floatArrayOf(0f, 0f, 0f, -6f, -12f, -15f, -15f, -12f, -6f, 0f),
        "No treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -4f, -9f, -14f, -15f),
        "Classic" to floatArrayOf(5f, 4f, 3f, 1f, 0f, -1f, 0f, 2f, 4f, 5f),
        "Dance" to floatArrayOf(8f, 7f, 5f, 1f, 0f, 0f, 2f, 3f, 4f, 3f),
        "Live" to floatArrayOf(-3f, -2f, 0f, 1f, 3f, 4f, 4f, 3f, 2f, 1f),
        "Speaker" to floatArrayOf(-9f, -7f, -4f, -1f, 2f, 3f, 4f, 3f, 1f, 0f),
        "Pop" to floatArrayOf(-1f, 0f, 1f, 2f, 3f, 3f, 2f, 0f, -1f, -2f),
        "Rock" to floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
        "Soft" to floatArrayOf(3f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f, -5f),
        "Techno" to floatArrayOf(8f, 6f, 3f, 0f, -2f, -1f, 1f, 4f, 6f, 7f)
    )
}
