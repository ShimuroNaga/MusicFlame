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
    }
}
