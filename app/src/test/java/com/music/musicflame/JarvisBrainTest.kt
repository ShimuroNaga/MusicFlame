package com.music.musicflame

import com.music.musicflame.data.JarvisBrain
import com.music.musicflame.data.JarvisCall
import com.music.musicflame.data.JarvisSelector
import com.music.musicflame.data.JarvisStep
import com.music.musicflame.data.JarvisTool
import com.music.musicflame.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la parte pura de Jarvis (atajos sin IA, detección de acciones y confirmaciones). Corren en JVM. */
class JarvisBrainTest {

    private fun song(id: Long) = Song(id = id, title = "t$id", artist = "a", duration = 1000L, path = "/x/$id.mp3")

    @Test fun quickPlan_controles_basicos() {
        assertEquals(JarvisTool.PAUSE, JarvisBrain.quickPlan("Pausa")!!.calls[0].tool)
        assertEquals(JarvisTool.NEXT, JarvisBrain.quickPlan("siguiente")!!.calls[0].tool)
        assertEquals(JarvisTool.PREVIOUS, JarvisBrain.quickPlan("canción anterior")!!.calls[0].tool)
        assertEquals(JarvisTool.RESUME, JarvisBrain.quickPlan("continúa")!!.calls[0].tool)
    }

    @Test fun quickPlan_temporizador_y_volumen() {
        val sleep = JarvisBrain.quickPlan("apágate en 30 min")!!.calls[0]
        assertEquals(JarvisTool.SLEEP_TIMER, sleep.tool)
        assertEquals(30, sleep.value)
        val vol = JarvisBrain.quickPlan("volumen al 40")!!.calls[0]
        assertEquals(JarvisTool.VOLUME, vol.tool)
        assertEquals(40, vol.value)
        assertEquals("up", JarvisBrain.quickPlan("sube el volumen")!!.calls[0].direction)
        assertEquals("down", JarvisBrain.quickPlan("baja")!!.calls[0].direction)
        assertEquals(JarvisTool.CANCEL_SLEEP_TIMER, JarvisBrain.quickPlan("cancela el temporizador")!!.calls[0].tool)
    }

    @Test fun quickPlan_no_atrapa_peticiones_largas() {
        assertNull(JarvisBrain.quickPlan("pon algo de Daft Punk para estudiar"))
        assertNull(JarvisBrain.quickPlan("hola, ¿cómo estás?"))
    }

    @Test fun looksLikeAction_distingue_charla_de_acciones() {
        assertTrue(JarvisBrain.looksLikeAction("márcame como favoritas las 5 más escuchadas"))
        assertTrue(JarvisBrain.looksLikeAction("Crea una playlist de rock de los 2000"))
        assertTrue(JarvisBrain.looksLikeAction("reproduce Around the World"))
        assertFalse(JarvisBrain.looksLikeAction("¿cuál es tu opinión sobre el jazz?"))
    }

    @Test fun confirmQuestion_solo_pide_confirmar_cambios_masivos() {
        val few = JarvisStep(JarvisCall(JarvisTool.UNFAVORITE, JarvisSelector(source = "favorites")), songs = listOf(song(1), song(2)))
        assertNull(JarvisBrain.confirmQuestion(listOf(few), "es"))
        val many = JarvisStep(JarvisCall(JarvisTool.UNFAVORITE, JarvisSelector(source = "favorites")), songs = (1L..5L).map(::song))
        assertNotNull(JarvisBrain.confirmQuestion(listOf(many), "es"))
        val create = JarvisStep(JarvisCall(JarvisTool.CREATE_PLAYLIST), songs = (1L..30L).map(::song), label = "x")
        assertNull(JarvisBrain.confirmQuestion(listOf(create), "es"))   // crear no es destructivo
    }

    @Test fun compose_une_frase_y_resultados() {
        assertEquals("Va.\n\n• uno\n• dos", JarvisBrain.compose("Va.", listOf("uno", "dos")))
        assertEquals("• uno", JarvisBrain.compose(null, listOf("uno")))
    }
}
