package com.music.musicflame.data

/**
 * Resultado del análisis REAL del audio de una canción (no depende de tags
 * de género ni de metadata que el usuario puso a mano).
 *
 * - bpm: tempo estimado en golpes por minuto. 0f si no se pudo estimar con
 *   confianza (canción muy corta, silencio, ruido sin pulso claro, etc.).
 * - energy: energía percibida normalizada entre 0f (muy suave/silenciosa) y
 *   1f (muy fuerte/intensa), calculada sobre una escala logarítmica (dB) en
 *   vez de RMS lineal, para que se parezca más a cómo el oído humano percibe
 *   el volumen.
 * - analyzedAt: epoch millis de cuándo se calculó, por si en el futuro se
 *   quiere invalidar resultados viejos.
 */
data class AudioFeatures(
    val bpm: Float,
    val energy: Float,
    val analyzedAt: Long = System.currentTimeMillis()
)
