package com.music.musicflame

import com.music.musicflame.data.JarvisBrain
import com.music.musicflame.data.JarvisTool
import com.music.musicflame.voice.VoiceAction
import com.music.musicflame.voice.VoiceBrain
import com.music.musicflame.voice.VoiceContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la parte pura del asistente de voz (sí/no, respaldo local y lista blanca). Corren en JVM. */
class VoiceBrainTest {

    private fun vc() = VoiceContext("Flame", "Ana", "es", 12, null, emptyList())

    @Test fun yesNo_entiende_respuestas_comunes() {
        assertEquals(true, VoiceBrain.yesNo("Sí, guárdala"))
        assertEquals(true, VoiceBrain.yesNo("yes"))
        assertEquals(false, VoiceBrain.yesNo("no"))
        assertEquals(false, VoiceBrain.yesNo("mejor no"))
        assertEquals(false, VoiceBrain.yesNo("Descártala"))
        assertNull(VoiceBrain.yesNo("quizá"))
        assertNull(VoiceBrain.yesNo(null))
    }

    @Test fun local_deshacer_y_que_suena() {
        assertTrue(VoiceBrain.local("deshaz eso", vc()) is VoiceAction.Undo)
        val st = VoiceBrain.local("¿qué suena?", vc())
        assertTrue(st is VoiceAction.Stats && st.kind == "now_playing")
    }

    @Test fun local_volumen_sin_internet() {
        val a = VoiceBrain.local("volumen al 40", vc())
        assertTrue(a is VoiceAction.Act && a.calls[0].tool == JarvisTool.VOLUME)
    }

    @Test fun lista_blanca_incluye_nuevas_herramientas_y_descarta_el_resto() {
        val plan = JarvisBrain.parsePlan(
            """{"say":"","actions":[{"tool":"repeat","direction":"one"},{"tool":"shuffle_mode","direction":"on"},
               {"tool":"rename_playlist","playlist":"Gym","name":"Pesas"},{"tool":"delete_playlist","name":"Gym"},{"tool":"delete_song"}]}"""
        )!!
        assertEquals(
            listOf(JarvisTool.REPEAT, JarvisTool.SHUFFLE_MODE, JarvisTool.RENAME_PLAYLIST),
            plan.calls.map { it.tool }
        )
    }

    @Test fun lista_blanca_incluye_eq_personalizacion_y_juntos() {
        val plan = JarvisBrain.parsePlan(
            """{"say":"","actions":[{"tool":"eq_preset","name":"Rock"},{"tool":"eq_band","name":"bass","direction":"up"},
               {"tool":"song_title","name":"Nuevo"},{"tool":"song_cover","direction":"reset"},
               {"tool":"together_create"},{"tool":"together_invite"},{"tool":"format_storage"}]}"""
        )!!
        assertEquals(
            listOf(JarvisTool.EQ_PRESET, JarvisTool.EQ_BAND, JarvisTool.SONG_TITLE, JarvisTool.SONG_COVER,
                JarvisTool.TOGETHER_CREATE, JarvisTool.TOGETHER_INVITE),
            plan.calls.map { it.tool }
        )
    }
}
