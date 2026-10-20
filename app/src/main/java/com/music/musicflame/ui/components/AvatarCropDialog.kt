package com.music.musicflame.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.musicflame.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.nativeCanvas
/**
 * Editor de recorte para el icono de "En compañía" (estilo Discord):
 * arrastra para mover y pellizca o usa el slider para el zoom (sin rotación).
 * El cuadrado de la rejilla es lo que se guarda; el círculo marca cómo lo verán los demás.
 *
 * [onConfirm] recibe un Bitmap cuadrado de [outSize] px.
 */
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    outSize: Int = 256
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { runCatching { decodeSampled(context, uri, 1600) }.getOrNull() }
        if (bmp == null) loadFailed = true else source = bmp
    }

    // Estado del editor
    var zoom by remember { mutableFloatStateOf(1f) }        // 1 = la imagen justo cubre el cuadrado
    var offset by remember { mutableStateOf(Offset.Zero) }  // px de pantalla, relativo al centro
    var cropSidePx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
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
                IconButton(
                    enabled = source != null,
                    onClick = {
                        val src = source ?: return@IconButton
                        onConfirm(renderCrop(src, outSize, cropSidePx, zoom, offset))
                    }
                ) { Icon(Icons.Filled.Check, null, tint = if (source != null) Color.White else Color.Gray) }
            }

            // Zona de edición (gris como en el editor de fotos)
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF8A8A8A)).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val src = source
                if (src == null) {
                    if (loadFailed) Text(stringResource(R.string.avatar_editor_load_failed), color = Color.White)
                    else CircularProgressIndicator(color = Color.White)
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    offset = clampOffset(src, cropSidePx, zoom, offset + pan)
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val side = min(size.width, size.height) * 0.86f
                            cropSidePx = side
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val cover = side / min(src.width, src.height)
                            val s = cover * zoom

                            // Imagen transformada (se ve completa por fuera del recorte)
                            withTransform({
                                translate(cx + offset.x, cy + offset.y)
                                scale(s, s, Offset.Zero)
                                translate(-src.width / 2f, -src.height / 2f)
                            }) {
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                                }
                            }

                            val left = cx - side / 2f
                            val top = cy - side / 2f

                            // Oscurece lo que queda fuera del cuadrado
                            val shade = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                                addRect(androidx.compose.ui.geometry.Rect(left, top, left + side, top + side))
                            }
                            drawPath(shade, Color.Black.copy(alpha = 0.45f))

                            // Marco + rejilla 3x3
                            drawRect(Color.White, Offset(left, top), Size(side, side), style = Stroke(2.dp.toPx()))
                            for (i in 1..2) {
                                val d = side / 3f * i
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(left + d, top), Offset(left + d, top + side), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(left, top + d), Offset(left + side, top + d), 1.dp.toPx())
                            }
                            // Círculo guía: así lo verán los demás
                            drawCircle(Color.White.copy(alpha = 0.85f), radius = side / 2f, center = Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }

            // Panel inferior: valor + slider de escala
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF232326)).padding(top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${(zoom * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        source?.let { src -> offset = clampOffset(src, cropSidePx, zoom, offset) }
                    },
                    valueRange = 1f..5f,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(6.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CropFree, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.avatar_editor_scale), color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Mantiene la imagen cubriendo todo el cuadrado (sin huecos), como en Discord. */
private fun clampOffset(src: Bitmap, side: Float, zoom: Float, o: Offset): Offset {
    if (side <= 0f) return o
    val s = side / min(src.width, src.height) * zoom
    val maxX = max(0f, src.width * s / 2f - side / 2f)
    val maxY = max(0f, src.height * s / 2f - side / 2f)
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}

/** Dibuja en un bitmap cuadrado exactamente lo que se ve dentro del recuadro del editor. */
private fun renderCrop(
    src: Bitmap, outSize: Int, cropSidePx: Float, zoom: Float, offsetRaw: Offset
): Bitmap {
    val side = if (cropSidePx > 0f) cropSidePx else outSize.toFloat()
    val offset = clampOffset(src, side, zoom, offsetRaw)
    val k = outSize / side                       // pantalla -> salida
    val cover = side / min(src.width, src.height)
    val m = Matrix().apply {
        postTranslate(-src.width / 2f, -src.height / 2f)
        postScale(cover * zoom * k, cover * zoom * k)
        postTranslate(outSize / 2f + offset.x * k, outSize / 2f + offset.y * k)
    }
    val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK) // JPEG no tiene transparencia
    canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Decodifica la imagen reduciéndola a [maxSide] px (respeta la orientación EXIF vía ImageDecoder). */
private fun decodeSampled(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val big = max(info.size.width, info.size.height)
        if (big > maxSide) {
            val f = maxSide.toFloat() / big
            decoder.setTargetSize(
                max(1, (info.size.width * f).toInt()),
                max(1, (info.size.height * f).toInt())
            )
        }
    }
}
