package com.music.musicflame.voice

/**
 * Modos de actitud del asistente de voz: cambian el tono del texto (lo que pide el prompt) y la voz del
 * sistema (velocidad y tono). Los textos visibles de cada modo viven en strings_voice.xml.
 */
enum class VoiceMode(val id: String, val speechRate: Float, val pitch: Float) {
    BUTLER("butler", 0.95f, 0.95f),
    FRIEND("friend", 1.0f, 1.0f),
    HYPE("hype", 1.15f, 1.12f),
    ZEN("zen", 0.85f, 0.88f),
    SARCASTIC("sarcastic", 1.0f, 0.92f);

    /** Descripción del estilo para el prompt del modelo (no es texto visible). */
    fun style(en: Boolean): String = when (this) {
        BUTLER -> if (en) "an elegant, warm and witty digital butler: courteous, with a touch of dry humor."
            else "un mayordomo digital elegante, cálido e ingenioso: cortés y con un toque de humor fino."
        FRIEND -> if (en) "a close friend: relaxed, spontaneous, informal, easy to talk to."
            else "un buen amigo: cercano, relajado, espontáneo e informal, fácil de platicar."
        HYPE -> if (en) "full of energy: enthusiastic, celebrates the music, dynamic phrases (no ALL CAPS, no emojis)."
            else "con la energía al máximo: entusiasta, celebra la música, frases dinámicas (sin MAYÚSCULAS ni emojis)."
        ZEN -> if (en) "calm and serene: slow, soft phrases that bring peace."
            else "tranquilo y sereno: frases suaves y pausadas que transmiten calma."
        SARCASTIC -> if (en) "gently sarcastic and playful: teases with affection, never rude or hurtful to the user."
            else "con sarcasmo suave y juguetón: bromea con cariño, nunca ofensivo ni hiriente con el usuario."
    }

    companion object {
        fun fromId(id: String?): VoiceMode = values().firstOrNull { it.id == id } ?: BUTLER
    }
}
