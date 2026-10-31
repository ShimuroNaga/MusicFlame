package com.music.musicflame.voice.wake

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado visible de la escucha siempre (lo leen la tarjeta de Ajustes y el indicador dentro de la app). */
enum class WakeState { OFF, STARTING, LISTENING, PAUSED_ASSISTANT, PAUSED_CALL, PAUSED_LOUD_MUSIC, PAUSED_BUSY_MIC, PAUSED_TEST }

/** Estado compartido entre el servicio de escucha, el asistente de voz y Ajustes (todo vive en el mismo proceso). */
object WakeWord {
    val state = MutableStateFlow(WakeState.OFF)

    /** true mientras el servicio de escucha esté vivo (para detectar paradas inesperadas). */
    @Volatile var serviceAlive = false

    /** true mientras el registro guiado o "Probar nombre" usan el micrófono: el servicio lo suelta. */
    @Volatile var testing = false

    @Volatile private var assistantOpen = false
    @Volatile private var holdUntil = 0L

    /** Volumen de música (0..1) a partir del cual se pausa la escucha si suena por el altavoz. */
    private const val LOUD_RATIO = 0.8f

    fun assistantOpened() { assistantOpen = true }

    /** El asistente se cerró: se espera un momento para no oír el final de su propia voz. */
    fun assistantClosed() { assistantOpen = false; holdUntil = SystemClock.elapsedRealtime() + 1_500L }

    /** Se acaba de lanzar el asistente por "oye <nombre>": se suelta el micrófono mientras abre. */
    fun launching() { assistantOpen = false; holdUntil = SystemClock.elapsedRealtime() + 12_000L }

    fun isAssistantOpen(): Boolean = assistantOpen

    /** Pausas baratas (variables en memoria); se consultan en cada bloque de audio. */
    fun quickPause(): WakeState? {
        if (testing) return WakeState.PAUSED_TEST
        if (assistantOpen || SystemClock.elapsedRealtime() < holdUntil) return WakeState.PAUSED_ASSISTANT
        return null
    }

    /** Pausas que consultan al sistema (llamada, música alta); se revisan cada ~1 s. */
    fun systemPause(ctx: Context): WakeState? {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return null
        val mode = am.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING) return WakeState.PAUSED_CALL
        if (musicLeaking(am)) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max >= LOUD_RATIO) return WakeState.PAUSED_LOUD_MUSIC
        }
        return null
    }

    /** Suena música por el altavoz del teléfono (con audífonos no se mete al micrófono). */
    fun musicLeaking(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return musicLeaking(am)
    }

    private fun musicLeaking(am: AudioManager): Boolean {
        if (!am.isMusicActive) return false
        val headsets = intArrayOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID
        )
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in headsets }
    }
}

/** Aviso suave (nunca bloquea) si el nombre de la IA puede ser difícil para el detector. */
object WakeNameCheck {
    enum class Hint { SHORT, ONE_SYLLABLE, LONG, SYMBOLS }

    fun hint(name: String): Hint? {
        val n = name.trim()
        if (n.isEmpty()) return null
        if (n.any { !it.isLetter() && it != ' ' && it != '-' }) return Hint.SYMBOLS
        val letters = WakeTemplates.normName(n).replace(" ", "")
        if (letters.length < 3) return Hint.SHORT
        val syllables = countSyllables(letters)
        if (syllables < 2) return Hint.ONE_SYLLABLE
        if (syllables > 4 || letters.length > 12) return Hint.LONG
        return null
    }

    /** Aproximación: grupos de vocales (suficiente para un aviso). */
    private fun countSyllables(s: String): Int {
        var count = 0
        var prevVowel = false
        for (c in s) {
            val v = c in "aeiouy"
            if (v && !prevVowel) count++
            prevVowel = v
        }
        return maxOf(1, count)
    }
}

/** Quita "oye <nombre>" del inicio de lo que dijiste, para que quede solo la orden. */
object WakePhrase {
    private val PREFIX = setOf("oye", "oiga", "hey", "ey", "eh", "ok", "okay", "hola", "hi", "hello")

    fun strip(text: String, aiName: String): String {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val nameTokens = WakeTemplates.normName(aiName).split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty() || nameTokens.isEmpty()) return text
        var i = 0
        var skipped = 0
        while (i < tokens.size && skipped < 2 && WakeTemplates.normName(tokens[i]) in PREFIX) { i++; skipped++ }
        var matched = 0
        for (nt in nameTokens) {
            if (i < tokens.size && similar(WakeTemplates.normName(tokens[i]), nt)) { i++; matched++ } else break
        }
        if (matched != nameTokens.size) return text
        return tokens.drop(i).joinToString(" ").trimStart(',', '.', ':', ';', '-', '!', '¡', ' ')
    }

    private fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val tol = when { b.length >= 6 -> 2; b.length >= 4 -> 1; else -> 0 }
        return tol > 0 && levenshtein(a, b) <= tol
    }

    private fun levenshtein(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }
}
