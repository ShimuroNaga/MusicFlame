package com.music.musicflame.data

import android.content.Context
import java.util.Locale

/**
 * Idioma de Jarvis y del asistente de voz. Códigos internos: "es" (idioma base), "en" y "pt"
 * (portugués de Brasil). Cualquier otro idioma del sistema cae a "es", igual que antes.
 *
 * Convención en el código de la IA: los TEXTOS QUE VE O ESCUCHA el usuario tienen tres versiones
 * (es / en / pt). Los PROMPTS para el modelo solo existen en español e inglés: en portugués se usa el
 * prompt en inglés y se le pide al modelo responder en portugués de Brasil ([outputLanguageName]).
 */
object JarvisLang {
    fun normalize(code: String?): String = when (code?.lowercase()?.substringBefore('-')?.substringBefore('_')) {
        "en" -> "en"
        "pt" -> "pt"
        else -> "es"
    }

    fun fromContext(context: Context): String = normalize(context.resources.configuration.locales[0].language)

    /** Idioma para el reconocimiento de voz de Android (RecognizerIntent.EXTRA_LANGUAGE). */
    fun speechTag(lang: String): String = when (lang) {
        "en" -> "en-US"
        "pt" -> "pt-BR"
        else -> "es-MX"
    }

    /** Locale del TextToSpeech. */
    fun ttsLocale(lang: String): Locale = if (lang == "pt") Locale("pt", "BR") else Locale(lang)

    /** Nombre del idioma de respuesta, para decírselo al modelo ("Reply in ..."). */
    fun outputLanguageName(lang: String): String = when (lang) {
        "en" -> "English"
        "pt" -> "Brazilian Portuguese"
        else -> "Spanish"
    }
}
