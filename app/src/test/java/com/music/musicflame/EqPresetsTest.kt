package com.music.musicflame

import com.music.musicflame.audio.EqPresets
import com.music.musicflame.audio.ProBiquadEqualizerAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los presets nuevos del ecualizador: tamaños y rangos válidos, sin nombres repetidos. */
class EqPresetsTest {

    private val requested = listOf(
        "Bass", "Mids", "Treble", "Bass and treble", "Full bass", "Full mids", "Full treble",
        "Reduce bass", "Reduce mids", "Reduce treble", "No bass", "No mids", "No treble",
        "Dance", "Live", "Speaker", "Soft", "Techno"
    )

    @Test
    fun freePresetsHaveFiveBandsInsideMinusOneToOne() {
        for ((name, p) in EqPresets.FREE) {
            assertEquals(name, 5, p.bands.size)
            assertTrue(name, p.bands.all { it in -1f..1f })
            p.bassBoost?.let { assertTrue(name, it in 0f..100f) }
        }
    }

    @Test
    fun proPresetsHaveTenBandsInsideTheProcessorRange() {
        for ((name, gains) in EqPresets.PRO) {
            assertEquals(name, ProBiquadEqualizerAudioProcessor.BAND_COUNT, gains.size)
            assertTrue(name, gains.all {
                it in ProBiquadEqualizerAudioProcessor.MIN_BAND_GAIN_DB..ProBiquadEqualizerAudioProcessor.MAX_BAND_GAIN_DB
            })
        }
    }

    @Test
    fun namesAreUniqueAndEveryRequestedPresetIsThere() {
        assertEquals(EqPresets.FREE_NAMES.size, EqPresets.FREE_NAMES.toSet().size)
        assertEquals(EqPresets.PRO.size, EqPresets.PRO.map { it.first }.toSet().size)
        assertTrue(EqPresets.FREE_NAMES.containsAll(requested))
        assertTrue(EqPresets.PRO.map { it.first }.containsAll(requested + listOf("Classic", "Pop", "Rock")))
    }

    @Test
    fun noPresetDuplicatesOneThatAlreadyExistedInTheFreeEq() {
        val existing = listOf("Flat", "Rock", "Pop", "Hip hop", "Jazz", "Classical", "Electronico", "Refuerzo de graves", "Refuerzo de agudos", "Vocales", "Customizar")
        assertTrue(EqPresets.FREE_NAMES.none { it in existing })
    }
}
