package com.music.musicflame.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun compara_por_numeros_no_por_texto() {
        assertTrue(UpdateChecker.isNewer("v3.25", "v3.9"))
        assertFalse(UpdateChecker.isNewer("v3.9", "v3.25"))
    }

    @Test fun iguales_y_distinto_largo() {
        assertFalse(UpdateChecker.isNewer("v3.25", "v3.25"))
        assertTrue(UpdateChecker.isNewer("v3.25.1", "v3.25"))
        assertFalse(UpdateChecker.isNewer("v3.25", "v3.25.0"))
    }

    @Test fun tag_sin_numeros_no_es_nuevo() {
        assertFalse(UpdateChecker.isNewer("latest", "v3.25"))
    }
}
