/*
 * Port a Kotlin/Compose de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en el renderer animado del paquete Flutter (animated_renderer.dart y
 * animated_widget.dart). Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 *
 * Aquí vive la única parte con dependencias de Android/Compose: el Composable [Blobatar], el reloj compartido
 * y la caché por semilla. El resto del paquete es Kotlin puro y tiene sus pruebas de paridad.
 */
package com.music.musicflame.ui.blobatar

import android.content.Context
import android.provider.Settings
import android.util.LruCache
import android.view.Choreographer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * Dibuja el blobatar de [name] (la semilla; en MusicFlame, el uid de la persona).
 *
 * @param size Tamaño cuadrado. Si se omite, el que imponga [modifier].
 * @param animated Movimiento en reposo (respiración, vaivén, parpadeo, mirada). Apagado = figura quieta.
 * @param expression Expresión a mostrar; null equivale a idle. Al cambiar, la pose y el color viajan solos.
 * @param desaturated Pinta la figura en grises (persona desconectada).
 */
@Composable
fun Blobatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    animated: Boolean = true,
    expression: Expression? = null,
    desaturated: Boolean = false,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val systemOff = remember { systemAnimationsOff(context) }
    val running = animated && !systemOff

    val art = remember(name) { BlobatarCache.get(name) }
    val morph = remember(art) { BlobatarMorph(art, expression) }
    LaunchedEffect(morph, expression, running) { morph.retarget(expression, running) }
    DisposableEffect(running) {
        if (running) BlobatarClock.acquire()
        onDispose { if (running) BlobatarClock.release() }
    }

    val clock = BlobatarClock.nowMs
    val headPaint = remember { Paint().apply { style = PaintingStyle.Fill; isAntiAlias = true } }
    val eyePaint = remember { Paint().apply { style = PaintingStyle.Fill; isAntiAlias = true } }

    var mod = modifier
    if (size != Dp.Unspecified) mod = mod.size(size)
    if (contentDescription != null) mod = mod.semantics { this.contentDescription = contentDescription }

    Canvas(mod) {
        // Todo se lee aquí, en la fase de dibujo: el reloj y la transición solo repintan, no recomponen.
        morph.touch()
        val elapsed = if (running) clock.longValue.toDouble() else 0.0
        val liveFull = morph.currentPose()
        val live = if (running) liveFull else liveFull.copy(rock = 0.0, shake = 0.0)
        val motion = BlobatarMotion.motionAt(
            art.seeds, elapsed, if (running) 1.0 else 0.0, shake = if (running) liveFull.shake else 0.0
        )
        var head = parseHex(morph.currentHead())
        var eye = parseHex(morph.currentEye())
        if (desaturated) { head = head.toGray(); eye = eye.toGray() }
        headPaint.color = head
        eyePaint.color = eye
        drawBlobatar(art, live, motion, headPaint, eyePaint)
    }
}

/** Una pasada del renderer animado de Flutter: los trazos quedan fijos y solo se mueven las transformaciones. */
private fun DrawScope.drawBlobatar(art: BlobatarArt, pose: Pose, motion: MotionFrame, headPaint: Paint, eyePaint: Paint) {
    drawIntoCanvas { canvas ->
        val side = minOf(size.width, size.height)
        if (side <= 0f) return@drawIntoCanvas
        canvas.save()
        canvas.translate((size.width - side) / 2f, (size.height - side) / 2f)
        canvas.scale(side / 100f, side / 100f)

        canvas.save()
        canvas.translate(motion.shake.first.toFloat(), motion.shake.second.toFloat())
        canvas.translate(50f, 50f)
        canvas.scale(motion.breathe.first.toFloat(), motion.breathe.second.toFloat())
        canvas.translate(-50f, -50f)
        canvas.translate(0f, (pose.bdy + motion.bob).toFloat())

        for (p in art.petals) canvas.drawPath(p, headPaint)
        for (p in art.extra) canvas.drawPath(p, headPaint)
        canvas.drawPath(art.body, headPaint)

        canvas.save()
        canvas.translate(motion.saccade.first.toFloat(), motion.saccade.second.toFloat())
        for (i in art.eyes.indices) {
            val eye = art.eyes[i]
            val side1 = if (i == 0) -1.0 else 1.0
            val selected = if (i == 0) 0.0 else 1.0
            val phase = selected * (1 - pose.rock) + pose.rock * ((1 + side1 * motion.thinkingPhase) / 2)
            val cx = eye.layout.cx.toFloat()
            val cy = eye.layout.cy.toFloat()
            val rot = eye.layout.rot.toFloat()

            canvas.save()
            canvas.translate((eye.layout.cx + pose.edx * side1).toFloat(), (eye.layout.cy + pose.edy + phase * pose.edy2).toFloat())
            canvas.rotate(((pose.tilt + selected * pose.tilt2) * side1 + eye.layout.rot * (1 - pose.lock)).toFloat())
            canvas.scale((pose.esx + selected * pose.esx2).toFloat(), (pose.esy + selected * pose.esy2).toFloat())
            canvas.rotate(-rot)
            canvas.translate(-cx, -cy)

            canvas.translate(cx, cy)
            canvas.rotate((motion.wrap.rotation * side1).toFloat())
            canvas.scale(
                (1 + motion.wrap.magnitudeX + motion.wrap.side * side1).toFloat(),
                (1 + motion.wrap.scaleY).toFloat()
            )
            canvas.rotate(rot)
            canvas.scale(1f, motion.blink.toFloat())
            canvas.rotate(-rot)
            canvas.translate(-cx, -cy)

            canvas.drawPath(eye.path, eyePaint)
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
}

private fun parseHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private fun Color.toGray(): Color {
    val l = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(l, l, l, alpha)
}

/** Con "Escala de duración de animación" en 0 (opciones de desarrollador / ahorro), la figura no se anima. */
private fun systemAnimationsOff(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Throwable) {
    false
}

// ---- Geometría ya convertida a Path de Compose, una vez por semilla ----

internal class BlobatarEyeArt(val layout: Eye, val path: Path)

internal class BlobatarArt(
    val palette: Map<String, String>,
    val seeds: MotionSeeds,
    val petals: List<Path>,
    val extra: List<Path>,
    val body: Path,
    val eyes: List<BlobatarEyeArt>
)

/** Caché por semilla: resolver (hash, paleta, siluetas) y convertir trazos cuesta; dibujar no debe pagarlo. */
internal object BlobatarCache {
    private val cache = LruCache<String, BlobatarArt>(64)

    fun get(name: String): BlobatarArt = synchronized(cache) {
        cache.get(name) ?: build(name).also { cache.put(name, it) }
    }

    private fun build(name: String): BlobatarArt {
        val (layout, palette) = BlobatarRender.partsFor(name)
        val eyePaths = layout.eyePaths()
        return BlobatarArt(
            palette = palette,
            seeds = BlobatarMotion.seedsFor(name),
            petals = layout.petals.map { p ->
                Path().apply { addOval(Rect(Offset(p.cx.toFloat(), p.cy.toFloat()), p.r.toFloat())) }
            },
            extra = layout.extra.map { it.toComposePath() },
            body = layout.bodyPath().toComposePath(),
            eyes = layout.eyes.mapIndexed { i, e -> BlobatarEyeArt(e, eyePaths[i].toComposePath()) }
        )
    }

    /** Recorre los segmentos 1:1, con los valores sin redondear (el redondeo a 2 decimales es solo del markup). */
    private fun BlobPath.toComposePath(): Path {
        val out = Path()
        var x = 0.0
        var y = 0.0
        for (seg in segments) {
            when (seg) {
                is PathSegment.MoveTo -> { out.moveTo(seg.x.toFloat(), seg.y.toFloat()); x = seg.x; y = seg.y }
                is PathSegment.LineTo -> { out.lineTo(seg.x.toFloat(), seg.y.toFloat()); x = seg.x; y = seg.y }
                is PathSegment.CubicTo -> {
                    out.cubicTo(seg.c1x.toFloat(), seg.c1y.toFloat(), seg.c2x.toFloat(), seg.c2y.toFloat(), seg.x.toFloat(), seg.y.toFloat())
                    x = seg.x; y = seg.y
                }
                is PathSegment.QuadTo -> {
                    out.quadraticTo(seg.cx.toFloat(), seg.cy.toFloat(), seg.x.toFloat(), seg.y.toFloat())
                    x = seg.x; y = seg.y
                }
                is PathSegment.HorizontalLineTo -> { out.lineTo(seg.x.toFloat(), y.toFloat()); x = seg.x }
                is PathSegment.VerticalLineTo -> { out.lineTo(x.toFloat(), seg.y.toFloat()); y = seg.y }
                PathSegment.ClosePath -> out.close()
            }
        }
        return out
    }
}

// ---- Reloj compartido ----

/**
 * Un solo reloj para todos los blobatars animados en pantalla. Corre mientras haya al menos uno y se detiene
 * solo cuando no queda ninguno. Cada figura lo lee en su fase de dibujo: un tic repinta, no recompone.
 * Solo se usa desde el hilo principal (los efectos de Compose corren ahí).
 */
internal object BlobatarClock {
    private val ms = mutableLongStateOf(0L)
    val nowMs: LongState get() = ms

    private var users = 0
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            ms.longValue = frameTimeNanos / 1_000_000L
            if (users > 0) Choreographer.getInstance().postFrameCallback(this) else running = false
        }
    }

    fun acquire() {
        users++
        if (!running) {
            running = true
            // Misma base de tiempo que el Choreographer, para que el primer cuadro no dé un salto.
            ms.longValue = System.nanoTime() / 1_000_000L
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    fun release() {
        if (users > 0) users--
    }
}

// ---- Transición de expresión (pose y colores) ----

/**
 * La expresión que se está mostrando y el viaje hacia la siguiente: lerp de la pose con la curva de entrada
 * (300 ms) o de salida a idle (400 ms), y los colores con fadeHex, igual que el widget animado original.
 */
internal class BlobatarMorph(private val art: BlobatarArt, initial: Expression?) {
    val progress = Animatable(1f)
    private val version = mutableIntStateOf(0)

    private var applied: Expression? = initial
    private var toIdle = isIdle(initial)
    private var fromPose = poseOf(initial)
    private var toPose = fromPose
    private var fromHead = headOf(initial)
    private var toHead = fromHead
    private var fromEye = eyeOf(initial)
    private var toEye = fromEye

    private fun isIdle(e: Expression?) = e == null || e == BlobatarExpressions.idle
    private fun poseOf(e: Expression?): Pose = e?.pose ?: BlobatarExpressions.identityPose
    private fun palette(e: Expression?) = BlobatarExpressions.expressionPalette(art.palette, e ?: BlobatarExpressions.idle)
    private fun headOf(e: Expression?) = palette(e).getValue(BlobatarColor.HEAD)
    private fun eyeOf(e: Expression?) = palette(e).getValue(BlobatarColor.EYE)

    /** Leerlo en el dibujo lo suscribe a los cambios de destino que no mueven [progress] (los cortes). */
    fun touch() { version.intValue }

    private fun eased(): Double {
        val p = progress.value.toDouble()
        return if (toIdle) BlobatarMotion.easeInOut(p) else BlobatarMotion.expressionEnterEase(p)
    }

    fun currentPose(): Pose =
        if (progress.value >= 1f) toPose else BlobatarMotion.lerpPose(fromPose, toPose, eased())

    fun currentHead(): String =
        if (progress.value >= 1f) toHead else BlobatarColor.fadeHex(fromHead, toHead, eased())

    fun currentEye(): String =
        if (progress.value >= 1f) toEye else BlobatarColor.fadeHex(fromEye, toEye, eased())

    private fun setTarget(expression: Expression?) {
        applied = expression
        toIdle = isIdle(expression)
        toPose = poseOf(expression)
        toHead = headOf(expression)
        toEye = eyeOf(expression)
        version.intValue++
    }

    /** Cambia de expresión: con animación si la figura se mueve; de golpe si no. */
    suspend fun retarget(expression: Expression?, animate: Boolean) {
        if (!animate) {
            if (applied != expression || progress.value != 1f) {
                setTarget(expression)
                fromPose = toPose; fromHead = toHead; fromEye = toEye
                progress.snapTo(1f)
            }
            return
        }
        if (applied == expression) return
        fromPose = currentPose(); fromHead = currentHead(); fromEye = currentEye()
        setTarget(expression)
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(if (toIdle) BlobatarMotion.EXPRESSION_EXIT_MS else BlobatarMotion.EXPRESSION_ENTER_MS, easing = LinearEasing)
        )
    }
}
