package com.music.musicflame.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulación de la "Pelea de stickmans" (EqualizerStyle.FIGHT).
 *
 * Es Kotlin puro (sin Compose, sin Android) a propósito: así la lógica se
 * puede probar sola y EqualizerStyleRenderers.kt solo se encarga de dibujar
 * lo que esta clase calcula. No carga imágenes ni video: cada luchador es un
 * esqueleto de 11 puntos que se recalcula por cuadro a partir de 11 ángulos.
 *
 * Cómo reacciona a la música (los niveles vienen de EqualizerLevelsState):
 *  - Tercio de GRAVES  -> luchador A (izquierda): golpes pesados (hook, uppercut,
 *    patada circular, patada voladora). Pega más duro y empuja más lejos.
 *  - Tercio de AGUDOS  -> luchador B (derecha): golpes rápidos (jabs en ráfaga,
 *    cross, patada alta).
 *  - Tercio de MEDIOS  -> no pega: marca el ritmo del rebote/pasos y qué tan
 *    cerca pelean (más volumen general = más pegados).
 *  - Cada banda se normaliza con su propio pico, así los agudos (que suenan más
 *    bajito) también pegan fuerte.
 *  - Un golpe sale cuando la banda SUBE de golpe (onset) o está sostenida muy
 *    alta; mientras más alta la banda, más rápido el golpe y menos espera entre
 *    golpes (a tope encadenan ráfagas). Con silencio solo hacen guardia.
 */

// ---------------------------------------------------------------------------
// Poses. 11 valores por pose, ángulos en grados medidos desde "colgando hacia
// abajo" (0 = abajo, 90 = horizontal hacia el frente, 180 = arriba).
//  0 inclinación del torso (+ = hacia el rival)   1 inclinación extra de la cabeza
//  2/3  brazo delantero: hombro-codo / codo-mano
//  4/5  brazo trasero:   hombro-codo / codo-mano
//  6/7  pierna delantera: cadera-rodilla / rodilla-pie
//  8/9  pierna trasera:   cadera-rodilla / rodilla-pie
// 10 salto (en unidades u hacia arriba)
// ---------------------------------------------------------------------------
private const val POSE_SIZE = 11

private fun pose(vararg v: Float): FloatArray {
    check(v.size == POSE_SIZE)
    return v.copyOf()
}

private val GUARD = pose(8f, 0f, 28f, 150f, 12f, 155f, 30f, 6f, -25f, -8f, 0f)
// Golpeado pero cubriéndose: se echa atrás con los antebrazos arriba protegiendo la cara.
private val HIT = pose(-20f, -10f, 50f, 160f, 35f, 165f, 12f, -12f, -28f, -8f, 0f)
// Bloqueo: agachado, antebrazos frente a la cara.
private val BLOCK = pose(12f, -4f, 55f, 165f, 40f, 170f, 36f, 12f, -28f, -14f, 0f)
// Rebote contra la pared: el cuerpo se despega con los brazos abiertos.
private val WALL = pose(16f, -6f, 60f, 80f, 35f, 70f, 18f, 0f, -18f, -8f, 0.08f)
private val DODGE = pose(-28f, -12f, 20f, 150f, 5f, 150f, 25f, 5f, -20f, -5f, 0f)

private val JAB_W = pose(6f, -3f, 20f, 160f, 12f, 155f, 32f, 8f, -26f, -8f, 0f)
private val JAB_S = pose(18f, 4f, 88f, 92f, 12f, 155f, 36f, 10f, -28f, -10f, 0f)

private val CROSS_W = pose(0f, -2f, 28f, 150f, -5f, 165f, 28f, 5f, -30f, -12f, 0f)
private val CROSS_S = pose(32f, 6f, 28f, 150f, 90f, 90f, 22f, 0f, -30f, -22f, 0f)

private val HOOK_W = pose(-8f, -4f, 28f, 150f, -55f, -20f, 30f, 6f, -25f, -8f, 0f)
private val HOOK_S = pose(40f, 8f, 28f, 150f, 100f, 92f, 40f, 12f, -30f, -15f, 0f)

private val UPPER_W = pose(-4f, 0f, 28f, 150f, 5f, 10f, 50f, -15f, -40f, -35f, 0f)
private val UPPER_S = pose(14f, -6f, 28f, 150f, 62f, 172f, 22f, 2f, -10f, -5f, 0.15f)

private val KICK_W = pose(-16f, -4f, 40f, 100f, -30f, -40f, 5f, 0f, -35f, -75f, 0f)
private val KICK_S = pose(-30f, -8f, 70f, 100f, -45f, -40f, 4f, 0f, 96f, 92f, 0f)

private val HIGHKICK_S = pose(-42f, -10f, 70f, 100f, -45f, -40f, 2f, 0f, 128f, 124f, 0f)

private val FLY_W = pose(-6f, 0f, 40f, 110f, 20f, 120f, 50f, -20f, -30f, -40f, 0.1f)
private val FLY_S = pose(-35f, -8f, 60f, 60f, -40f, -40f, 40f, 85f, 98f, 94f, 0.95f)

// tip: qué punta pega. 0 mano delantera, 1 mano trasera, 2 pie delantero, 3 pie trasero.
internal class FightMove(
    val windup: FloatArray,
    val strike: FloatArray,
    val tip: Int,
    val reachU: Float,
    val power: Float,
    val lunge: Float,
    val dur: Float
)

private val JAB = FightMove(JAB_W, JAB_S, 0, 2.5f, 0.5f, 0.5f, 0.30f)
private val CROSS = FightMove(CROSS_W, CROSS_S, 1, 2.7f, 0.8f, 0.8f, 0.36f)
private val HOOK = FightMove(HOOK_W, HOOK_S, 1, 2.8f, 1.0f, 0.9f, 0.42f)
private val UPPERCUT = FightMove(UPPER_W, UPPER_S, 1, 2.0f, 1.1f, 0.6f, 0.40f)
private val KICK = FightMove(KICK_W, KICK_S, 3, 2.3f, 1.3f, 0.8f, 0.50f)
private val HIGH_KICK = FightMove(KICK_W, HIGHKICK_S, 3, 2.2f, 1.2f, 0.7f, 0.52f)
private val FLYING_KICK = FightMove(FLY_W, FLY_S, 3, 2.6f, 1.6f, 1.8f, 0.58f)

// Índices de los puntos del esqueleto (cada uno ocupa 2 floats: x, y).
internal const val SK_HIP = 0
internal const val SK_SHOULDER = 1
internal const val SK_HEAD = 2
internal const val SK_ELBOW_L = 3
internal const val SK_HAND_L = 4
internal const val SK_ELBOW_R = 5
internal const val SK_HAND_R = 6
internal const val SK_KNEE_L = 7
internal const val SK_FOOT_L = 8
internal const val SK_KNEE_R = 9
internal const val SK_FOOT_R = 10

internal const val TRAIL_N = 6

internal class FightFighter(val dir: Float, val isBass: Boolean) {
    var x = 0f
    var vx = 0f
    val shown: FloatArray = GUARD.copyOf()
    val target: FloatArray = GUARD.copyOf()
    val skel = FloatArray(22)

    var move: FightMove? = null
    var moveT = 0f
    var moveSpeed = 1f
    var struck = false
    var cd = 0.2f
    var stagger = 0f
    var staggerDur = 0.3f
    var dodge = 0f
    var block = 0f
    var slam = 0f
    var counterPending = false
    var cornered = false
    var flash = 0f
    var drive = 0f
    var attacks = 0
    var blocksMade = 0
    var slams = 0
    var counters = 0
    var combo = 0
    var hitsTaken = 0

    var band = 0f
    var avg = 0f
    var peak = 0.3f
    var idleTime = 0f
    var phase = 0f

    val trail = FloatArray(TRAIL_N * 2)
    var trailLen = 0
}

internal class FightSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float)

internal class FightImpact(val x: Float, val y: Float, val power: Float, var age: Float = 0f)

internal class FightSim(seed: Int = 20261011) {
    private val rnd = Random(seed)

    val a = FightFighter(dir = 1f, isBass = true)
    val b = FightFighter(dir = -1f, isBass = false)
    val sparks = ArrayList<FightSpark>()
    val impacts = ArrayList<FightImpact>()

    var u = 10f
        private set
    var ground = 0f
        private set
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    /** Marca de tiempo del último cuadro (la usa el Canvas para calcular dt). */
    var lastNanos = 0L

    // Contadores (útiles para probar la simulación sin dibujar nada).
    var attacksStarted = 0
        private set
    var hitsLanded = 0
        private set
    var whiffs = 0
        private set
    var blocksLanded = 0
        private set

    private var inited = false
    private var time = 0f
    private var hitStop = 0f
    private var shake = 0f
    private var drift = 0f
    private var overall = 0f
    private var midPeak = 0.3f
    private var beatAvg = 0f
    private var lastBeat = -10f
    private var tempo = 0f

    /** 0..1: destello de la pared izquierda/derecha cuando alguien se estrella contra ella. */
    var wallFlashL = 0f
        private set
    var wallFlashR = 0f
        private set
    private var width = 0f

    fun update(levels: FloatArray, barCount: Int, w: Float, h: Float, dtReal: Float) {
        if (w <= 0f || h <= 0f || barCount <= 0) return
        width = w
        u = min(h * 0.2f, w * 0.075f).coerceAtLeast(1f)
        ground = h * 0.94f
        time += dtReal

        // Congelado breve al conectar un golpe fuerte (hit-stop): da sensación de impacto.
        var dt = dtReal
        if (hitStop > 0f) {
            hitStop -= dtReal
            dt = dtReal * 0.05f
        }

        // --- 1) Bandas de frecuencia normalizadas ---
        val third = max(1, barCount / 3)
        updateBand(a, levels, 0, third, dtReal)
        updateBand(b, levels, barCount - third, barCount, dtReal)
        val midRaw = bandRaw(levels, third, min(barCount, third * 2))
        midPeak = max(midRaw, midPeak - dtReal * 0.15f)
        val mid = if (midRaw < 0.03f) 0f else (midRaw / max(midPeak, 0.3f)).coerceIn(0f, 1f)
        val overallTarget = (a.band + mid + b.band) / 3f
        overall += (overallTarget - overall) * (1f - exp(-dtReal * 5f))

        // Ambos luchadores pelean con la música completa (no solo con su banda), así
        // nunca se queda uno parado aunque su banda esté floja en esa canción.
        a.drive = max(a.band, 0.85f * overall)
        b.drive = max(b.band, 0.85f * overall)

        // Tempo: cuántos golpes de ritmo por segundo. Canción rápida = pelea más rápida.
        val beatSig = max(a.band, b.band)
        if (beatSig > 0.3f && beatSig - beatAvg > 0.15f && time - lastBeat > 0.08f) {
            val inst = (1f / (time - lastBeat)).coerceIn(0f, 10f)
            tempo += (inst - tempo) * 0.3f
            lastBeat = time
        } else if (time - lastBeat > 0.6f) {
            tempo *= exp(-dtReal * 0.8f)
        }
        beatAvg += (beatSig - beatAvg) * (1f - exp(-dtReal / 0.35f))

        // Acorralado = pegado a un extremo de la pantalla.
        val edge = 1.2f * u + 1.6f * u
        a.cornered = a.x < edge || a.x > w - edge
        b.cornered = b.x < edge || b.x > w - edge

        // --- 2) Posiciones objetivo (qué tan cerca pelean depende del volumen) ---
        val gap = lerp(8f * u, 3.3f * u, overall.pow(0.7f)).coerceAtMost(w * 0.8f)
        drift += (0f - drift) * (1f - exp(-dtReal * 0.9f))
        drift = drift.coerceIn(-0.2f * w, 0.2f * w)
        val center = w * 0.5f + drift + sin(time * 0.5f) * w * 0.03f
        val margin = 1.4f * u
        val targetA = (center - gap / 2f).coerceIn(margin, w - margin)
        val targetB = (center + gap / 2f).coerceIn(margin, w - margin)
        if (!inited) {
            a.x = targetA
            b.x = targetB
            inited = true
        }

        // --- 3) Cada luchador decide, avanza su golpe y se mueve ---
        stepFighter(a, b, targetA, w, dt, dtReal)
        stepFighter(b, a, targetB, w, dt, dtReal)

        // Que nunca se atraviesen.
        if (b.x - a.x < 1.7f * u) {
            val m = (a.x + b.x) / 2f
            a.x = m - 0.85f * u
            b.x = m + 0.85f * u
        }

        // --- 4) Poses finales + esqueletos ---
        for (f in arrayOf(a, b)) {
            computeTarget(f)
            val rate = when {
                f.slam > 0f -> 34f
                f.block > 0f -> 30f
                f.stagger > 0f -> 38f
                f.dodge > 0f -> 26f
                f.move != null -> 20f * max(1f, f.moveSpeed)
                else -> 14f
            }
            val k = 1f - exp(-dt * rate)
            for (i in 0 until POSE_SIZE) f.shown[i] += (f.target[i] - f.shown[i]) * k
            computeSkel(f)
            updateTrail(f)
            f.flash = max(0f, f.flash - dtReal * 5f)
        }

        // --- 5) Chispas, ondas de impacto y temblor de pantalla ---
        for (i in sparks.indices.reversed()) {
            val s = sparks[i]
            s.x += s.vx * dtReal
            s.y += s.vy * dtReal
            s.vy += 38f * u * dtReal
            s.life -= dtReal
            if (s.life <= 0f) sparks.removeAt(i)
        }
        for (i in impacts.indices.reversed()) {
            val im = impacts[i]
            im.age += dtReal / 0.28f
            if (im.age >= 1f) impacts.removeAt(i)
        }
        shake *= exp(-dtReal * 12f)
        wallFlashL = max(0f, wallFlashL - dtReal * 4f)
        wallFlashR = max(0f, wallFlashR - dtReal * 4f)
        shakeX = (rnd.nextFloat() - 0.5f) * 2f * shake * 0.35f * u
        shakeY = (rnd.nextFloat() - 0.5f) * 2f * shake * 0.35f * u
    }

    // ---------------------------------------------------------------- bandas

    private fun bandRaw(levels: FloatArray, from: Int, to: Int): Float {
        val end = min(to, levels.size)
        if (from >= end) return 0f
        var sum = 0f
        var mx = 0f
        for (i in from until end) {
            val v = levels[i].coerceIn(0f, 1f)
            sum += v
            if (v > mx) mx = v
        }
        return 0.55f * (sum / (end - from)) + 0.45f * mx
    }

    private fun updateBand(f: FightFighter, levels: FloatArray, from: Int, to: Int, dtReal: Float) {
        val raw = bandRaw(levels, from, to)
        f.peak = max(raw, f.peak - dtReal * 0.15f)
        f.band = if (raw < 0.03f) 0f else (raw / max(f.peak, 0.3f)).coerceIn(0f, 1f)
    }

    // --------------------------------------------------------- lógica de pelea

    private fun stepFighter(f: FightFighter, o: FightFighter, targetX: Float, w: Float, dt: Float, dtReal: Float) {
        f.phase += dt * (4f + 10f * overall)
        f.avg += (f.drive - f.avg) * (1f - exp(-dtReal / 0.35f))
        if (f.stagger > 0f) {
            f.stagger -= dt
            if (f.stagger <= 0f) {
                f.cd = 0f // al reponerse contraataca enseguida
                f.counterPending = rnd.nextFloat() < 0.85f
            }
        }
        if (f.dodge > 0f) {
            f.dodge -= dt
            if (f.dodge <= 0f) {
                f.cd = 0f
                f.counterPending = true // esquivó: castiga al rival
            }
        }
        if (f.block > 0f) {
            f.block -= dt
            if (f.block <= 0f) {
                f.cd = 0f
                f.counterPending = rnd.nextFloat() < 0.8f // bloqueó: contragolpe
            }
        }
        if (f.slam > 0f) {
            f.slam -= dt
            if (f.slam <= 0f) {
                f.cd = 0f
                f.counterPending = rnd.nextFloat() < 0.9f // rebota de la pared y responde
            }
        }
        if (f.cd > 0f) f.cd -= dt

        val m = f.move
        if (m != null) {
            f.moveT += dt * f.moveSpeed / m.dur
            if (!f.struck && f.moveT >= 0.45f) {
                f.struck = true
                resolveStrike(f, o, m)
            }
            if (f.moveT >= 1f) {
                f.move = null
                f.cd = lerp(0.38f, 0f, f.drive) * (0.7f + rnd.nextFloat() * 0.5f) / (1f + tempoBoost())
                if (f.combo >= 3) {
                    // Tras 3 golpes seguidos retrocede un instante: le da chance al rival de responder.
                    f.cd = max(f.cd, 0.45f / (1f + 0.5f * tempoBoost()))
                    f.combo = 0
                }
                f.idleTime = 0f
            }
        } else {
            f.idleTime += dt
            val free = f.stagger <= 0f && f.dodge <= 0f && f.block <= 0f && f.slam <= 0f && f.cd <= 0f
            if (free) {
                val onset = f.drive > 0.3f && f.drive - f.avg > 0.15f
                val sustained = f.drive > 0.7f
                val restless = overall > 0.25f && f.idleTime > (0.6f - 0.4f * overall) / (1f + tempoBoost())
                val counter = f.counterPending && overall > 0.08f
                if (counter) {
                    f.counterPending = false
                    f.counters++
                    startMove(f, o)
                } else if (onset || sustained || restless) {
                    startMove(f, o)
                }
            }
        }

        // Física simple de resorte: vuelve a su sitio, pero con el golpe en curso
        // se suelta para poder lanzarse sobre el rival.
        val k = when {
            f.move != null -> 5f
            f.stagger > 0f -> 10f
            else -> 38f
        }
        f.vx += ((targetX - f.x) * k - f.vx * 9f) * dt
        f.x += f.vx * dt
        val margin = 1.2f * u
        if (f.x < margin) {
            f.x = margin
            if (-f.vx > 9f * u) wallSlam(f, true)
            f.vx = abs(f.vx) * 0.4f
        } else if (f.x > w - margin) {
            f.x = w - margin
            if (f.vx > 9f * u) wallSlam(f, false)
            f.vx = -abs(f.vx) * 0.4f
        }
    }

    private fun startMove(f: FightFighter, o: FightFighter) {
        val m = pickMove(f)
        f.move = m
        f.moveT = 0f
        f.struck = false
        f.moveSpeed = ((1f + (if (f.isBass) 2.4f else 2.0f) * f.drive) * (1f + tempoBoost()) * (0.92f + rnd.nextFloat() * 0.16f)).coerceAtMost(3.8f)
        attacksStarted++
        f.attacks++

        // Salto hacia el rival para llegar siempre al golpe, más el empuje propio del golpe.
        val need = max(0f, abs(o.x - f.x) - m.reachU * u * 0.8f)
        val dash = min(12f * need, 80f * u) + m.lunge * 8f * u
        f.vx += f.dir * dash

        // Reacción del rival: se cubre (más seguido si está acorralado), esquiva o se come el golpe.
        if (o.move == null && o.stagger <= 0f && o.dodge <= 0f && o.block <= 0f) {
            val pBlock = if (o.cornered || o.hitsTaken >= 2) 0.85f else 0.45f
            val pDodge = if (o.cornered) 0.1f else 0.18f
            val r = rnd.nextFloat()
            val window = 0.75f * m.dur / f.moveSpeed + 0.1f
            if (r < pBlock) {
                o.block = window
            } else if (r < pBlock + pDodge) {
                o.dodge = window
                o.vx += -o.dir * 10f * u
            }
        }
    }

    private fun pickMove(f: FightFighter): FightMove {
        val b = f.band
        val r = rnd.nextFloat()
        return if (f.isBass) {
            when {
                b < 0.4f -> if (r < 0.5f) CROSS else HOOK
                b < 0.7f -> when {
                    r < 0.35f -> HOOK
                    r < 0.65f -> UPPERCUT
                    else -> KICK
                }
                else -> when {
                    r < 0.35f -> KICK
                    r < 0.6f -> FLYING_KICK
                    r < 0.85f -> HOOK
                    else -> HIGH_KICK
                }
            }
        } else {
            when {
                b < 0.4f -> JAB
                b < 0.7f -> when {
                    r < 0.5f -> JAB
                    r < 0.8f -> CROSS
                    else -> UPPERCUT
                }
                else -> when {
                    r < 0.5f -> JAB
                    r < 0.8f -> CROSS
                    else -> HIGH_KICK
                }
            }
        }
    }

    private fun resolveStrike(f: FightFighter, o: FightFighter, m: FightMove) {
        val tipIdx = when (m.tip) {
            0 -> SK_HAND_L
            1 -> SK_HAND_R
            2 -> SK_FOOT_L
            else -> SK_FOOT_R
        }
        val tipY = f.skel[tipIdx * 2 + 1].coerceIn(ground - 4f * u, ground - 0.3f * u)
        val dist = abs(o.x - f.x)

        if (o.dodge > 0f || dist > m.reachU * u + 1.3f * u) {
            // Falló: solo una estela de aire.
            whiffs++
            spawnSparks(f.x + f.dir * m.reachU * u * 0.9f, tipY, f.dir, 3, 0.3f)
            f.vx += f.dir * 3f * u
            return
        }

        if (o.block > 0f && !(m.power >= 1.3f && rnd.nextFloat() < 0.3f)) {
            // Golpe bloqueado: chispas chicas sobre los antebrazos, empuje corto, sin tambaleo.
            blocksLanded++
            o.blocksMade++
            val guardY = o.skel[SK_SHOULDER * 2 + 1] - 0.3f * u
            spawnSparks(o.x + o.dir * 0.8f * u, guardY, f.dir, 4, 0.3f)
            impacts.add(FightImpact(o.x + o.dir * 0.8f * u, guardY, 0.3f))
            o.vx += f.dir * (4f + 4f * m.power) * u
            f.vx -= f.dir * 2f * u
            shake = max(shake, 0.2f + 0.2f * m.power)
            hitStop = max(hitStop, 0.02f)
            o.block = min(o.block, 0.14f)
            f.combo = 0
            o.hitsTaken = 0
            return
        }

        hitsLanded++
        f.combo++
        f.hitsTaken = 0
        o.combo = 0
        o.hitsTaken++
        val contactX = o.x - f.dir * 0.5f * u
        spawnSparks(contactX, tipY, f.dir, (6 + 10 * m.power).toInt(), m.power)
        impacts.add(FightImpact(contactX, tipY, m.power))

        // El golpeado se interrumpe, sale volando hacia atrás y se tambalea.
        o.move = null
        o.dodge = 0f
        o.block = 0f
        o.staggerDur = max(0.14f, 0.32f / (1f + 0.6f * f.band))
        o.stagger = o.staggerDur
        o.flash = 1f
        o.vx += f.dir * (12f + 12f * m.power) * u

        shake = max(shake, 0.4f + 0.6f * m.power)
        hitStop = max(hitStop, 0.04f + 0.05f * m.power)
        drift += f.dir * 0.05f * m.power * width
    }

    private fun tempoBoost(): Float = (tempo / 4f).coerceIn(0f, 1.2f)

    private fun wallSlam(f: FightFighter, left: Boolean) {
        f.slam = 0.18f
        f.stagger = 0f
        f.move = null
        f.slams++
        val wx = if (left) 0f else width
        val y = f.skel[SK_SHOULDER * 2 + 1]
        spawnSparks(wx + (if (left) 0.4f else -0.4f) * u, y, if (left) 1f else -1f, 8, 0.8f)
        impacts.add(FightImpact(wx + (if (left) 0.3f else -0.3f) * u, y, 0.8f))
        if (left) wallFlashL = 1f else wallFlashR = 1f
        shake = max(shake, 0.7f)
    }

    private fun spawnSparks(x: Float, y: Float, dir: Float, count: Int, power: Float) {
        val speed = 14f * u * (0.6f + 0.6f * power)
        repeat(count) {
            if (sparks.size >= 80) return
            val vx = (dir * (0.4f + rnd.nextFloat()) + (rnd.nextFloat() - 0.5f) * 1.2f) * speed
            val vy = (rnd.nextFloat() - 0.7f) * speed * 1.1f
            val life = 0.25f + rnd.nextFloat() * 0.25f
            sparks.add(FightSpark(x, y, vx, vy, life, life))
        }
    }

    // -------------------------------------------------------------------- poses

    private fun computeTarget(f: FightFighter) {
        val out = f.target
        val m = f.move
        when {
            f.slam > 0f -> System.arraycopy(WALL, 0, out, 0, POSE_SIZE)
            f.block > 0f -> System.arraycopy(BLOCK, 0, out, 0, POSE_SIZE)
            f.stagger > 0f -> {
                System.arraycopy(HIT, 0, out, 0, POSE_SIZE)
                val flail = sin(time * 40f) * 6f
                out[2] += flail
                out[3] += flail
                out[4] -= flail
            }
            f.dodge > 0f -> System.arraycopy(DODGE, 0, out, 0, POSE_SIZE)
            m != null -> movePose(m, f.moveT, out)
            else -> {
                // Guardia con rebote: más amplio y rápido mientras más fuerte suena.
                // Acorralado: guardia cerrada y agachada en vez de la guardia normal.
                System.arraycopy(if (f.cornered) BLOCK else GUARD, 0, out, 0, POSE_SIZE)
                val amp = 0.25f + 0.75f * overall
                val s = sin(f.phase)
                out[0] += s * 3f * amp
                out[6] += s * 6f * amp
                out[8] -= s * 6f * amp
                out[10] = max(0f, sin(f.phase * 2f)) * 0.07f * amp
            }
        }
    }

    private fun movePose(m: FightMove, t: Float, out: FloatArray) {
        for (i in 0 until POSE_SIZE) {
            out[i] = when {
                t < 0.30f -> lerp(GUARD[i], m.windup[i], smooth(t / 0.30f))
                t < 0.48f -> lerp(m.windup[i], m.strike[i], easeOut((t - 0.30f) / 0.18f))
                t < 0.66f -> m.strike[i]
                else -> lerp(m.strike[i], GUARD[i], smooth((t - 0.66f) / 0.34f))
            }
        }
    }

    private fun computeSkel(f: FightFighter) {
        val p = f.shown
        val s = f.skel
        val d = f.dir
        val uu = u

        val legL = uu * (cos(rad(p[6])) + cos(rad(p[7])))
        val legR = uu * (cos(rad(p[8])) + cos(rad(p[9])))
        val hipY = ground - max(legL, legR) - p[10] * uu
        setPt(s, SK_HIP, f.x, hipY)

        val lean = rad(p[0])
        val shX = f.x + d * sin(lean) * 1.35f * uu
        val shY = hipY - cos(lean) * 1.35f * uu
        setPt(s, SK_SHOULDER, shX, shY)

        val headA = rad(p[0] + p[1])
        setPt(s, SK_HEAD, shX + d * sin(headA) * 0.6f * uu, shY - cos(headA) * 0.6f * uu)

        limb(s, SK_ELBOW_L, SK_HAND_L, shX, shY, p[2], p[3], 0.85f * uu, d)
        limb(s, SK_ELBOW_R, SK_HAND_R, shX, shY, p[4], p[5], 0.85f * uu, d)
        limb(s, SK_KNEE_L, SK_FOOT_L, f.x, hipY, p[6], p[7], 1.0f * uu, d)
        limb(s, SK_KNEE_R, SK_FOOT_R, f.x, hipY, p[8], p[9], 1.0f * uu, d)
    }

    private fun limb(
        s: FloatArray, midIdx: Int, endIdx: Int,
        ox: Float, oy: Float, a1: Float, a2: Float, len: Float, d: Float
    ) {
        val r1 = rad(a1)
        val mx = ox + d * sin(r1) * len
        val my = oy + cos(r1) * len
        setPt(s, midIdx, mx, my)
        val r2 = rad(a2)
        setPt(s, endIdx, mx + d * sin(r2) * len, my + cos(r2) * len)
    }

    private fun updateTrail(f: FightFighter) {
        val m = f.move
        if (m != null && f.moveT in 0.25f..0.72f) {
            for (i in TRAIL_N - 1 downTo 1) {
                f.trail[i * 2] = f.trail[(i - 1) * 2]
                f.trail[i * 2 + 1] = f.trail[(i - 1) * 2 + 1]
            }
            val idx = when (m.tip) {
                0 -> SK_HAND_L
                1 -> SK_HAND_R
                2 -> SK_FOOT_L
                else -> SK_FOOT_R
            }
            f.trail[0] = f.skel[idx * 2]
            f.trail[1] = f.skel[idx * 2 + 1]
            f.trailLen = min(TRAIL_N, f.trailLen + 1)
        } else if (f.trailLen > 0) {
            f.trailLen--
        }
    }

    // ------------------------------------------------------------------ utilidades

    private fun setPt(s: FloatArray, idx: Int, x: Float, y: Float) {
        s[idx * 2] = x
        s[idx * 2 + 1] = y
    }

    private fun rad(deg: Float): Float = deg * (PI.toFloat() / 180f)
    private fun lerp(x: Float, y: Float, t: Float): Float = x + (y - x) * t
    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
    private fun easeOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c)
    }
}
