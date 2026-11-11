/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/traits.ts y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 */
package com.music.musicflame.ui.blobatar

import kotlin.math.floor

/**
 * Lector de rasgos sobre una semilla ya hasheada.
 *
 * Cada valor se pide por clave de texto (no de una secuencia), así que las claves son un espacio de
 * nombres "solo se agrega": introducir una clave nueva no altera ningún otro rasgo. Lo único que NO se
 * puede cambiar es el contenido de los arreglos de `pick` (el índice de la opción es parte del mapeo).
 *
 * [overrides] fija rasgos concretos. Cada valor es una posición en [0, 1): un número fija la clave a un
 * resultado; una lista significa "cualquiera de estos" (el hash de la clave elige); una lista vacía es
 * igual que omitir la clave. Los valores se recortan (no se confía en ellos).
 */
class Traits(val state: Int, private val overrides: Map<String, Any>? = null) {

    /** Float uniforme en [0, 1). */
    operator fun invoke(key: String): Double {
        val v = overrides?.get(key)
        var o: Double? = null
        if (v is List<*>) {
            if (v.isNotEmpty()) {
                val index = floor(BlobatarHash.stream(state, key) * v.size).toInt()
                val chosen = v[index]
                if (chosen != null) o = (chosen as Number).toDouble()
            }
        } else if (v is Number) {
            o = v.toDouble()
        }
        // Un override de 0 es válido (es el piso de todo rango): no debe caer al hash.
        if (o == null) return BlobatarHash.stream(state, key)
        if (o > 0) return if (o < 1) o else 0.999999
        return 0.0 // también cubre NaN
    }

    /** Float uniforme en [min, max). */
    fun numIn(key: String, min: Double, max: Double): Double = min + this(key) * (max - min)

    /** Entero uniforme de [min] a [max], ambos incluidos. */
    fun intIn(key: String, min: Int, max: Int): Int = min + floor(this(key) * (max - min + 1)).toInt()

    /** Elección uniforme. Agregar opciones reasigna las semillas existentes: congelado por versión mayor. */
    fun <T> pick(key: String, options: List<T>): T = options[floor(this(key) * options.size).toInt()]

    /** Verdadero con probabilidad [p]. */
    fun boolIn(key: String, p: Double = 0.5): Boolean = this(key) < p

    /** Variación simétrica en [-amount, amount). */
    fun jitter(key: String, amount: Double): Double = (this(key) * 2 - 1) * amount

    companion object {
        /**
         * Crea el lector para una semilla. Se normaliza (NFC, trim, minúsculas) salvo que [normalize]
         * sea false.
         */
        fun forSeed(seed: String, normalize: Boolean = true, overrides: Map<String, Any>? = null): Traits =
            Traits(BlobatarHash.seedState(seed, normalize), overrides)
    }
}
