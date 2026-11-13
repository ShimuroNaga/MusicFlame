package com.music.musicflame.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.musicflame.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Recorte de PROPORCIÓN FIJA: la imagen se ve completa y fija; el recuadro se mueve (arrastrando
 * dentro) y se agranda o achica arrastrando sus esquinas o bordes, pero SIEMPRE conserva la
 * proporción [aspect] (ancho/alto), así la selección nunca se deforma.
 *
 * [onConfirm] recibe el recorte con el tamaño exacto [outWidth] x ([outWidth] / [aspect]) px.
 */
@Composable
fun FreeCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    outWidth: Int = 960,
    aspect: Float = 3f
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var crop by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { runCatching { decodeSampled(context, uri, 1600) }.getOrNull() }
        if (bmp == null) loadFailed = true else source = bmp
    }

    // Recuadro inicial: el más grande con proporción [aspect] que cabe en la imagen
    fun defaultCrop(src: Bitmap, area: IntSize): Rect {
        val ir = fitRect(Size(area.width.toFloat(), area.height.toFloat()), src.width, src.height)
        val cw = min(ir.width, ir.height * aspect)
        val ch = cw / aspect
        val l = ir.left + (ir.width - cw) / 2f
        val t = ir.top + (ir.height - ch) / 2f
        return Rect(l, t, l + cw, t + ch)
    }

    LaunchedEffect(source, areaSize) {
        val src = source
        if (src != null && crop == null && areaSize.width > 0 && areaSize.height > 0) {
            crop = defaultCrop(src, areaSize)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
            // Barra superior
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = Color.White) }
                Text(
                    stringResource(R.string.avatar_editor_title),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                val ready = source != null && crop != null
                IconButton(
                    enabled = ready,
                    onClick = {
                        val src = source ?: return@IconButton
                        val c = crop ?: return@IconButton
                        onConfirm(renderFreeCrop(src, areaSize, c, outWidth, aspect))
                    }
                ) { Icon(Icons.Filled.Check, null, tint = if (ready) Color.White else Color.Gray) }
            }

            // Zona de edición
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF3A3A3C)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val src = source
                if (src == null) {
                    if (loadFailed) Text(stringResource(R.string.avatar_editor_load_failed), color = Color.White)
                    else CircularProgressIndicator(color = Color.White)
                } else {
                    val image = remember(src) { src.asImageBitmap() }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { areaSize = it }
                            .pointerInput(src) {
                                var mode = DragMode.None
                                val slop = 28.dp.toPx()
                                val minSide = 56.dp.toPx()
                                val area = Size(size.width.toFloat(), size.height.toFloat())
                                val bounds = fitRect(area, src.width, src.height)
                                detectDragGestures(
                                    onDragStart = { p -> mode = crop?.let { hitTest(it, p, slop) } ?: DragMode.None },
                                    onDragEnd = { mode = DragMode.None },
                                    onDragCancel = { mode = DragMode.None },
                                    onDrag = { change, delta ->
                                        val r = crop
                                        if (r != null && mode != DragMode.None) {
                                            change.consume()
                                            crop = applyDrag(r, mode, delta, bounds, minSide, aspect)
                                        }
                                    }
                                )
                            }
                    ) {
                        val ir = fitRect(size, src.width, src.height)
                        drawImage(
                            image,
                            dstOffset = IntOffset(ir.left.roundToInt(), ir.top.roundToInt()),
                            dstSize = IntSize(ir.width.roundToInt(), ir.height.roundToInt())
                        )
                        val c = crop ?: return@Canvas

                        // Oscurece lo que queda fuera del recuadro
                        val shade = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(ir)
                            addRect(c)
                        }
                        drawPath(shade, Color.Black.copy(alpha = 0.55f))

                        // Marco + rejilla 3x3
                        drawRect(Color.White, c.topLeft, c.size, style = Stroke(2.dp.toPx()))
                        for (i in 1..2) {
                            val dx = c.width / 3f * i
                            val dy = c.height / 3f * i
                            drawLine(Color.White.copy(alpha = 0.55f), Offset(c.left + dx, c.top), Offset(c.left + dx, c.bottom), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.55f), Offset(c.left, c.top + dy), Offset(c.right, c.top + dy), 1.dp.toPx())
                        }

                        // Asas: esquinas (círculos) y puntos medios de los bordes (barritas)
                        val rad = 7.dp.toPx()
                        listOf(c.topLeft, c.topRight, c.bottomLeft, c.bottomRight).forEach {
                            drawCircle(Color.White, rad, it)
                        }
                        val bw = 18.dp.toPx(); val bh = 5.dp.toPx()
                        listOf(
                            Offset(c.center.x, c.top) to Size(bw, bh),
                            Offset(c.center.x, c.bottom) to Size(bw, bh),
                            Offset(c.left, c.center.y) to Size(bh, bw),
                            Offset(c.right, c.center.y) to Size(bh, bw)
                        ).forEach { (p, s) ->
                            drawRoundRect(
                                Color.White,
                                Offset(p.x - s.width / 2f, p.y - s.height / 2f),
                                s,
                                androidx.compose.ui.geometry.CornerRadius(bh / 2f)
                            )
                        }
                    }
                }
            }

            // Panel inferior: ayuda + proporción + restablecer
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF232326)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.banner_crop_hint), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%d × %d px".format(outWidth, (outWidth / aspect).roundToInt()),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = {
                        val src = source
                        if (src != null && areaSize.width > 0) crop = defaultCrop(src, areaSize)
                    }) { Text(stringResource(R.string.banner_crop_reset)) }
                }
            }
        }
    }
}

private enum class DragMode { None, Move, L, T, R, B, TL, TR, BL, BR }

/** Rectángulo (en px del canvas) que ocupa la imagen al ajustarla centrada dentro del área. */
private fun fitRect(area: Size, w: Int, h: Int): Rect {
    val s = min(area.width / w, area.height / h)
    val iw = w * s
    val ih = h * s
    val l = (area.width - iw) / 2f
    val t = (area.height - ih) / 2f
    return Rect(l, t, l + iw, t + ih)
}

private fun hitTest(r: Rect, p: Offset, slop: Float): DragMode {
    val inY = p.y in (r.top - slop)..(r.bottom + slop)
    val inX = p.x in (r.left - slop)..(r.right + slop)
    val l = abs(p.x - r.left) <= slop && inY
    val rt = abs(p.x - r.right) <= slop && inY
    val t = abs(p.y - r.top) <= slop && inX
    val b = abs(p.y - r.bottom) <= slop && inX
    return when {
        l && t -> DragMode.TL
        rt && t -> DragMode.TR
        l && b -> DragMode.BL
        rt && b -> DragMode.BR
        l -> DragMode.L
        rt -> DragMode.R
        t -> DragMode.T
        b -> DragMode.B
        r.contains(p) -> DragMode.Move
        else -> DragMode.None
    }
}

/** Ajusta [w] a [lo]..[hi]; si el espacio disponible es menor que el mínimo, manda el disponible. */
private fun clampW(w: Float, lo: Float, hi: Float): Float = if (hi <= lo) hi else w.coerceIn(lo, hi)

/**
 * Mueve o redimensiona el recuadro SIN cambiar su proporción [aspect] (ancho/alto):
 *  - Move: desplaza el recuadro dentro de la imagen.
 *  - Esquinas: la esquina opuesta queda fija y el recuadro crece/encoge hacia la esquina arrastrada.
 *  - Bordes: el borde opuesto queda fijo y el otro eje se ajusta centrado para mantener la proporción.
 * Siempre queda dentro de [bounds] (la imagen) y con al menos [minSide] px de alto.
 */
private fun applyDrag(
    r: Rect, mode: DragMode, d: Offset, bounds: Rect,
    minSide: Float, aspect: Float
): Rect {
    if (mode == DragMode.Move) {
        val dx = d.x.coerceIn(bounds.left - r.left, bounds.right - r.right)
        val dy = d.y.coerceIn(bounds.top - r.top, bounds.bottom - r.bottom)
        return r.translate(dx, dy)
    }
    val minW = minSide * aspect
    val maxWAll = min(bounds.width, bounds.height * aspect)

    return when (mode) {
        DragMode.L, DragMode.R -> {
            val right = mode == DragMode.R
            val rawW = if (right) (r.right + d.x) - r.left else r.right - (r.left + d.x)
            val avail = if (right) bounds.right - r.left else r.right - bounds.left
            val w = clampW(rawW, minW, min(avail, maxWAll))
            val h = w / aspect
            val left = if (right) r.left else r.right - w
            val top = (r.center.y - h / 2f).coerceIn(bounds.top, bounds.bottom - h)
            Rect(left, top, left + w, top + h)
        }
        DragMode.T, DragMode.B -> {
            val bottom = mode == DragMode.B
            val rawH = if (bottom) (r.bottom + d.y) - r.top else r.bottom - (r.top + d.y)
            val avail = if (bottom) bounds.bottom - r.top else r.bottom - bounds.top
            val h = clampW(rawH, minSide, min(avail, maxWAll / aspect))
            val w = h * aspect
            val top = if (bottom) r.top else r.bottom - h
            val left = (r.center.x - w / 2f).coerceIn(bounds.left, bounds.right - w)
            Rect(left, top, left + w, top + h)
        }
        else -> { // TL, TR, BL, BR
            val leftCorner = mode == DragMode.TL || mode == DragMode.BL
            val topCorner = mode == DragMode.TL || mode == DragMode.TR
            val ax = if (leftCorner) r.right else r.left     // esquina opuesta (fija)
            val ay = if (topCorner) r.bottom else r.top
            val px = (if (leftCorner) r.left else r.right) + d.x
            val py = (if (topCorner) r.top else r.bottom) + d.y
            val wx = if (leftCorner) ax - px else px - ax
            val hy = if (topCorner) ay - py else py - ay
            val rawW = (wx + hy * aspect) / 2f
            val availW = if (leftCorner) ax - bounds.left else bounds.right - ax
            val availH = if (topCorner) ay - bounds.top else bounds.bottom - ay
            val w = clampW(rawW, minW, min(availW, availH * aspect))
            val h = w / aspect
            val left = if (leftCorner) ax - w else ax
            val top = if (topCorner) ay - h else ay
            Rect(left, top, left + w, top + h)
        }
    }
}

/** Recorta de la imagen original lo que encierra el recuadro y lo deja en el tamaño exacto pedido. */
private fun renderFreeCrop(src: Bitmap, area: IntSize, c: Rect, outWidth: Int, aspect: Float): Bitmap {
    val ir = fitRect(Size(area.width.toFloat(), area.height.toFloat()), src.width, src.height)
    val k = src.width / ir.width                       // canvas -> píxeles de la imagen
    val sl = ((c.left - ir.left) * k).coerceIn(0f, src.width - 1f)
    val st = ((c.top - ir.top) * k).coerceIn(0f, src.height - 1f)
    val sr = ((c.right - ir.left) * k).coerceIn(sl + 1f, src.width.toFloat())
    val sb = ((c.bottom - ir.top) * k).coerceIn(st + 1f, src.height.toFloat())
    // Tamaño de salida EXACTO (ancho fijo y alto según la proporción), sin importar el tamaño de la selección
    val outW = outWidth.coerceAtLeast(1)
    val outH = max(1, (outW / aspect).roundToInt())
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK) // JPEG no tiene transparencia
    canvas.drawBitmap(
        src,
        android.graphics.Rect(sl.toInt(), st.toInt(), sr.toInt(), sb.toInt()),
        android.graphics.RectF(0f, 0f, outW.toFloat(), outH.toFloat()),
        Paint(Paint.FILTER_BITMAP_FLAG)
    )
    return out
}
