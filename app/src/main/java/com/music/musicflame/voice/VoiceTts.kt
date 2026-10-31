package com.music.musicflame.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Voz del sistema del asistente: aplica idioma, voz elegida, y velocidad/tono (actitud x ajuste del usuario). */
object VoiceTts {

    /** Configura un TextToSpeech ya inicializado. Si la voz guardada ya no existe, usa la del sistema. */
    fun apply(t: TextToSpeech, s: VoiceSettings, lang: String) {
        t.language = Locale(lang)
        val saved = s.getTtsVoice()
        if (saved.isNotEmpty()) {
            try { t.voices?.firstOrNull { it.name == saved }?.let { t.voice = it } } catch (_: Exception) {}
        }
        val m = s.getMode()
        t.setSpeechRate((m.speechRate * s.getTtsRate()).coerceIn(0.4f, 2.0f))
        t.setPitch((m.pitch * s.getTtsPitch()).coerceIn(0.5f, 2.0f))
    }

    /** Voces instaladas para el idioma, de mejor a peor calidad (orden estable por nombre). */
    fun voicesFor(t: TextToSpeech, lang: String): List<Voice> = try {
        t.voices.orEmpty()
            .filter { it.locale.language == lang && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
    } catch (_: Exception) { emptyList() }
}
