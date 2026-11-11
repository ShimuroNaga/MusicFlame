/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en el contraparte aritmético de motion.css y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 *
 * Movimiento determinista por tiempo transcurrido: cada ciclo es una función pura del reloj compartido,
 * los tiempos por semilla, la amplitud ambiente y los canales de la expresión. Así se puede repintar desde
 * un reloj sin acumular estado ni regenerar trazos.
 */
package com.music.musicflame.ui.blobatar

import kotlin.math.abs
import kotlin.math.floor

/** Periodos, fases y dirección de la mirada de un blobatar, derivados de su semilla (en milisegundos). */
data class MotionSeeds(
    val phase: Int,
    val bob: Int,
    val blink: Int,
    val blinkPhase: Int,
    val saccade: Int,
    val saccadePhase: Int,
    val lookX: Double,
    val lookY: Double,
    /** Distancias sin signo, usadas al "envolver" el ojo. */
    val lookMagnitudeX: Double,
    val lookMagnitudeY: Double
)

/** Coeficientes de escorzo del ojo secundario durante una sácada, en un cuadro. */
data class MotionWrap(val magnitudeX: Double, val side: Double, val scaleY: Double, val rotation: Double)

/** Un cuadro determinista de todos los canales de movimiento en reposo. */
class MotionFrame(
    /** Temblor de la expresión "mad", en unidades del viewBox. */
    val shake: Pair<Double, Double>,
    /** Escalas horizontal y vertical de la respiración. */
    val breathe: Pair<Double, Double>,
    /** Traslación vertical del cuerpo. */
    val bob: Double,
    /** Traslación compartida de los ojos. */
    val saccade: Pair<Double, Double>,
    /** Fase del balanceo de "thinking", de -1 a 1. */
    val thinkingPhase: Double,
    /** Escala vertical del ojo; 1 es abierto. */
    val blink: Double,
    val wrap: MotionWrap
)

object BlobatarMotion {
    private fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

    private fun round2(v: Double): Double = jsRound(v * 100) / 100.0

    /** Deriva los valores exactos de movimiento a partir de un lector de rasgos. */
    fun seeds(t: Traits): MotionSeeds {
        val blink = jsRound(t.numIn("motion.blink", 3500.0, 6500.0))
        val saccade = jsRound(t.numIn("motion.saccade", 4200.0, 7600.0))
        val lookX = round2(t.numIn("motion.lookX", 1.0, 2.2))
        val lookY = round2(t.numIn("motion.lookY", 0.8, 1.7))
        return MotionSeeds(
            phase = jsRound(t.numIn("motion.phase", 0.0, 2800.0)),
            bob = jsRound(t.numIn("motion.bob", 0.0, 3400.0)),
            blink = blink,
            blinkPhase = jsRound(t.numIn("motion.blinkPhase", 0.0, blink.toDouble())),
            saccade = saccade,
            saccadePhase = jsRound(t.numIn("motion.saccadePhase", 0.0, saccade.toDouble())),
            lookX = lookX * (if (t.boolIn("motion.lookXFlip")) -1 else 1),
            lookY = lookY * (if (t.boolIn("motion.lookYFlip")) -1 else 1),
            lookMagnitudeX = lookX,
            lookMagnitudeY = lookY
        )
    }

    /** Deriva el movimiento de un nombre y sus opciones de rasgos. */
    fun seedsFor(name: String, normalize: Boolean = true, traits: Map<String, Any>? = null): MotionSeeds =
        seeds(Traits.forSeed(name, normalize, traits))

    private fun cycle(time: Double, phase: Double, period: Double): Double {
        val v = (time + phase) / period
        return v - floor(v)
    }

    private fun alternate(time: Double, phase: Double, period: Double): Double {
        val v = (time + phase) / period
        val iteration = floor(v)
        val fraction = v - iteration
        return if ((iteration.toLong() and 1L) == 1L) 1 - fraction else fraction
    }

    private fun stops(value: Double, table: Array<DoubleArray>, column: Int): Double {
        for (index in table.size - 1 downTo 0) {
            val row = table[index]
            if (value < row[0]) continue
            if (index + 1 == table.size) return row[column]
            val next = table[index + 1]
            val span = next[0] - row[0]
            if (span <= 0) return row[column]
            return row[column] + (next[column] - row[column]) * ((value - row[0]) / span)
        }
        return table.first()[column]
    }

    private val saccadeStops = arrayOf(
        doubleArrayOf(0.0, 0.0, 0.0),
        doubleArrayOf(0.15, 0.0, 0.0),
        doubleArrayOf(0.165, -0.8, -0.9),
        doubleArrayOf(0.31, -0.8, -0.9),
        doubleArrayOf(0.325, 1.0, 0.1),
        doubleArrayOf(0.47, 1.0, 0.1),
        doubleArrayOf(0.485, -0.15, 0.85),
        doubleArrayOf(0.63, -0.15, 0.85),
        doubleArrayOf(0.645, 0.75, -0.8),
        doubleArrayOf(0.79, 0.75, -0.8),
        doubleArrayOf(0.805, -1.0, -0.15),
        doubleArrayOf(0.985, -1.0, -0.15),
        doubleArrayOf(1.0, 0.0, 0.0)
    )

    private val wrapStops = arrayOf(
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.15, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.165, -0.0176, 0.008, -0.027, 0.648),
        doubleArrayOf(0.31, -0.0176, 0.008, -0.027, 0.648),
        doubleArrayOf(0.325, -0.022, -0.01, -0.003, 0.09),
        doubleArrayOf(0.47, -0.022, -0.01, -0.003, 0.09),
        doubleArrayOf(0.485, -0.0033, 0.0015, -0.0255, -0.115),
        doubleArrayOf(0.63, -0.0033, 0.0015, -0.0255, -0.115),
        doubleArrayOf(0.645, -0.0165, -0.0075, -0.024, -0.54),
        doubleArrayOf(0.79, -0.0165, -0.0075, -0.024, -0.54),
        doubleArrayOf(0.805, -0.022, 0.01, -0.0045, 0.135),
        doubleArrayOf(0.985, -0.022, 0.01, -0.0045, 0.135),
        doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
    )

    private val shakeStops = arrayOf(
        doubleArrayOf(0.0, 0.62, -0.34),
        doubleArrayOf(0.25, -0.7, 0.22),
        doubleArrayOf(0.5, 0.38, 0.66),
        doubleArrayOf(0.75, -0.44, -0.6),
        doubleArrayOf(1.0, 0.62, -0.34)
    )

    /** Duración de una iteración de la respiración antes de alternar. */
    const val BREATHE_MS = 2800.0

    /** Duración de una iteración del vaivén antes de alternar. */
    const val BOB_MS = 3400.0

    /** Duración de un ciclo del balanceo de "thinking". */
    const val THINKING_MS = 900.0

    /** Duración de un ciclo del temblor de "mad". */
    const val SHAKE_MS = 112.0

    /** Transición hacia una expresión que no es idle. */
    const val EXPRESSION_ENTER_MS = 300

    /** Transición de vuelta a idle. */
    const val EXPRESSION_EXIT_MS = 400

    /** Cambio de amplitud del movimiento ambiente. */
    const val AMBIENT_RAMP_MS = 400

    const val HOVER_ENTER_MS = 220
    const val HOVER_EXIT_MS = 160

    /** Resuelve una curva cúbica de Bézier de CSS en la fracción [x] del tiempo. */
    fun cubicBezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val cx = 3 * x1
        val bx = 3 * (x2 - x1) - cx
        val ax = 1 - cx - bx
        val cy = 3 * y1
        val by = 3 * (y2 - y1) - cy
        val ay = 1 - cy - by
        var parameter = x
        for (iteration in 0 until 8) {
            val error = ((ax * parameter + bx) * parameter + cx) * parameter - x
            if (abs(error) < 1e-5) break
            val derivative = (3 * ax * parameter + 2 * bx) * parameter + cx
            if (abs(derivative) < 1e-6) break
            parameter -= error / derivative
        }
        return ((ay * parameter + by) * parameter + cy) * parameter
    }

    fun easeInOut(v: Double) = cubicBezier(v, 0.42, 0.0, 0.58, 1.0)
    fun easeIn(v: Double) = cubicBezier(v, 0.42, 0.0, 1.0, 1.0)
    fun easeOut(v: Double) = cubicBezier(v, 0.0, 0.0, 0.58, 1.0)

    /** La curva de entrada de una expresión. */
    fun expressionEnterEase(v: Double) = cubicBezier(v, 0.45, 0.05, 0.5, 1.0)

    /** La curva de la reacción al pasar el dedo/cursor. */
    fun hoverEase(v: Double) = cubicBezier(v, 0.23, 1.0, 0.32, 1.0)

    /** Evalúa todos los canales de movimiento a los [elapsedMs] milisegundos. */
    fun motionAt(seeds: MotionSeeds, elapsedMs: Double, amplitude: Double, shake: Double = 0.0): MotionFrame {
        val breathe = easeInOut(alternate(elapsedMs, seeds.phase.toDouble(), BREATHE_MS))
        val bob = easeInOut(alternate(elapsedMs, seeds.bob.toDouble(), BOB_MS))
        val saccade = cycle(elapsedMs, seeds.saccadePhase.toDouble(), seeds.saccade.toDouble())
        val shakeCycle = cycle(elapsedMs, 0.0, SHAKE_MS)
        val thinkingCycle = cycle(elapsedMs, 0.0, THINKING_MS)
        val thinkingPhase =
            if (thinkingCycle < 0.5) 1 - 2 * easeInOut(thinkingCycle * 2)
            else -1 + 2 * easeInOut(thinkingCycle * 2 - 1)
        val blinkCycle = cycle(elapsedMs, seeds.blinkPhase.toDouble(), seeds.blink.toDouble())
        val blink =
            if (blinkCycle < 0.972) 1.0
            else if (blinkCycle < 0.986) 1 - 0.92 * amplitude * easeIn((blinkCycle - 0.972) / 0.014)
            else 1 - 0.92 * amplitude * (1 - easeOut((blinkCycle - 0.986) / 0.014))

        return MotionFrame(
            shake = (stops(shakeCycle, shakeStops, 1) * shake) to (stops(shakeCycle, shakeStops, 2) * shake),
            breathe = (1 + 0.022 * amplitude * breathe) to (1 - 0.018 * amplitude * breathe),
            bob = -1.1 * amplitude * bob,
            saccade = (stops(saccade, saccadeStops, 1) * seeds.lookX * amplitude) to
                (stops(saccade, saccadeStops, 2) * seeds.lookY * amplitude),
            thinkingPhase = thinkingPhase,
            blink = blink,
            wrap = MotionWrap(
                magnitudeX = stops(saccade, wrapStops, 1) * seeds.lookMagnitudeX * amplitude,
                side = stops(saccade, wrapStops, 2) * seeds.lookX * amplitude,
                scaleY = stops(saccade, wrapStops, 3) * seeds.lookMagnitudeY * amplitude,
                rotation = stops(saccade, wrapStops, 4) * seeds.lookX * seeds.lookY * amplitude
            )
        )
    }

    /** Interpola linealmente todos los canales de la expresión. Null equivale a la pose neutra. */
    fun lerpPose(from: Pose?, to: Pose?, progress: Double): Pose {
        val a = from ?: BlobatarExpressions.identityPose
        val b = to ?: BlobatarExpressions.identityPose
        fun lerp(s: Double, e: Double) = s * (1 - progress) + e * progress
        return Pose(
            esx = lerp(a.esx, b.esx), esy = lerp(a.esy, b.esy), tilt = lerp(a.tilt, b.tilt),
            edy = lerp(a.edy, b.edy), edx = lerp(a.edx, b.edx),
            esx2 = lerp(a.esx2, b.esx2), esy2 = lerp(a.esy2, b.esy2), tilt2 = lerp(a.tilt2, b.tilt2),
            edy2 = lerp(a.edy2, b.edy2), lock = lerp(a.lock, b.lock), heat = lerp(a.heat, b.heat),
            shake = lerp(a.shake, b.shake), rock = lerp(a.rock, b.rock), bdy = lerp(a.bdy, b.bdy)
        )
    }
}
