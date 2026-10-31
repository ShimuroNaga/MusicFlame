package com.music.musicflame.voice

import android.content.Context
import com.music.musicflame.data.SettingsStore

/** Ajustes del asistente de voz (card "Asistente de voz" en Ajustes > IA). */
class VoiceSettings(context: Context) {
    private val prefs = SettingsStore.get(context.applicationContext)

    /** Apagado por defecto: mientras esté apagado, ni el botón power ni el tile hacen nada. */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** true = el asistente contesta en voz alta (voz del sistema). */
    fun speaksReplies(): Boolean = prefs.getBoolean(KEY_SPEAK, true)
    fun setSpeaksReplies(v: Boolean) = prefs.edit().putBoolean(KEY_SPEAK, v).apply()

    /** Nombre con el que el asistente te llama. Vacío = primer nombre de tu cuenta de Google (si hay sesión). */
    fun getCustomName(): String = prefs.getString(KEY_NAME, "")?.trim().orEmpty()
    fun setCustomName(v: String) = prefs.edit().putString(KEY_NAME, v.trim().take(24)).apply()

    /** Actitud del asistente (cambia el estilo del texto y la voz del sistema). */
    fun getMode(): VoiceMode = VoiceMode.fromId(prefs.getString(KEY_MODE, null))
    fun setMode(m: VoiceMode) = prefs.edit().putString(KEY_MODE, m.id).apply()

    /** Prompt personalizado del usuario: SOLO afecta a la voz y solo a personalidad y estilo. */
    fun getCustomPrompt(): String = prefs.getString(KEY_PROMPT, "")?.trim().orEmpty().take(MAX_PROMPT)
    fun setCustomPrompt(v: String) = prefs.edit().putString(KEY_PROMPT, v.take(MAX_PROMPT)).apply()

    /** Voz del sistema elegida (nombre interno de TextToSpeech.Voice). Vacío = la voz por defecto del sistema. */
    fun getTtsVoice(): String = prefs.getString(KEY_TTS_VOICE, "")?.trim().orEmpty()
    fun setTtsVoice(v: String) = prefs.edit().putString(KEY_TTS_VOICE, v).apply()

    /** Multiplicadores de velocidad y tono que se suman a los de la actitud (1.0 = sin cambio). */
    fun getTtsRate(): Float = prefs.getFloat(KEY_TTS_RATE, 1f).coerceIn(MIN_TTS_MULT, MAX_TTS_MULT)
    fun setTtsRate(v: Float) = prefs.edit().putFloat(KEY_TTS_RATE, v.coerceIn(MIN_TTS_MULT, MAX_TTS_MULT)).apply()
    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1f).coerceIn(MIN_TTS_MULT, MAX_TTS_MULT)
    fun setTtsPitch(v: Float) = prefs.edit().putFloat(KEY_TTS_PITCH, v.coerceIn(MIN_TTS_MULT, MAX_TTS_MULT)).apply()

    /** Nombre final para saludar (llamar desde un hilo de fondo si puede tocar la cuenta de Google). */
    fun resolveUserName(ctx: Context): String {
        getCustomName().takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(ctx)
                ?.displayName?.trim()?.substringBefore(' ').orEmpty()
        } catch (_: Exception) { "" }
    }

    companion object {
        const val KEY_NAME = "voice_user_name"
        const val KEY_ENABLED = "voice_assistant_enabled"
        const val KEY_SPEAK = "voice_assistant_speak"
        const val KEY_MODE = "voice_mode"
        const val KEY_PROMPT = "voice_custom_prompt"
        const val MAX_PROMPT = 250
        const val KEY_TTS_VOICE = "voice_tts_voice"
        const val KEY_TTS_RATE = "voice_tts_rate"
        const val KEY_TTS_PITCH = "voice_tts_pitch"
        const val MIN_TTS_MULT = 0.6f
        const val MAX_TTS_MULT = 1.4f
    }
}
