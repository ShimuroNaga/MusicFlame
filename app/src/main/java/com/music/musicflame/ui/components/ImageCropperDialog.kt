package com.music.musicflame.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/**
 * Diálogo de recorte reutilizable: pan + zoom libre sobre un marco de aspect ratio fijo.
 * El aspect ratio lo decide quien lo llama (1f para carátulas cuadradas -formato AlbumArt-,
 * 16f/9f o el que corresponda para el fondo de SettingsScreen, etc). Devuelve un Bitmap ya
 * recortado, en píxeles reales de la imagen original (no de pantalla).
 *
 * Uso:
 * var showCropper by remember { mutableStateOf(false) }
 * if (showCropper) {
 *     ImageCropperDialog(
 *         imageUri = uriSeleccionada,
 *         aspectRatio = 1f, // 1f = carátula cuadrada, 16f/9f = fondo ancho, etc
 *         onCropped = { bitmap ->
 *             // pasar a RealTagWriter para carátula, o guardar como fondo en SettingsRepository
 *         },
 *         onDismiss = { showCropper = false }
 *     )
 * }
 *
 * Solo úsalo para jpg/png (imágenes estáticas). No lo llames para gif.
 */
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    aspectRatio: Float,
    onCropped: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    outputMaxDimensionPx: Int = 1600
) {
    val context = LocalContext.current

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf(false) }

    // Estado de transformación, vive aquí arriba (no en un mutableState de archivo)
    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var frameSizePx by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(imageUri) {
        sourceBitmap = null
        loadError = false
        try {
            val decoded = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (decoded == null) {
                loadError = true
            } else {
                val rotationDegrees = context.contentResolver.openInputStream(imageUri)?.use { s ->
                    val exif = ExifInterface(s)
                    when (exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                sourceBitmap = if (rotationDegrees != 0f) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                } else {
                    decoded
                }
            }
        } catch (e: Exception) {
            loadError = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                    }
                    Text("Ajusta la imagen", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    when {
                        loadError -> Text(
                            "No se pudo cargar la imagen",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        bmp == null -> CircularProgressIndicator(color = Color.White)
                        else -> CropFrame(
                            bitmap = bmp,
                            aspectRatio = aspectRatio,
                            scale = userScale,
                            offset = userOffset,
                            onFrameSizeChanged = { frameSizePx = it },
                            onScaleChange = { userScale = it },
                            onOffsetChange = { userOffset = it }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            if (frameSizePx == Size.Zero) return@Button
                            val cropRect = computeCropRect(
                                bitmapWidth = bmp.width,
                                bitmapHeight = bmp.height,
                                userScale = userScale,
                                userOffset = userOffset,
                                frameWidthPx = frameSizePx.width,
                                frameHeightPx = frameSizePx.height
                            )
                            onCropped(cropBitmap(bmp, cropRect, outputMaxDimensionPx))
                        },
                        enabled = sourceBitmap != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aplicar")
                    }
                }
            }
        }
    }
}

@Composable
private fun CropFrame(
    bitmap: Bitmap,
    aspectRatio: Float,
    scale: Float,
    offset: Offset,
    onFrameSizeChanged: (Size) -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val imageBitmap: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val minUserScale = 1f
    val maxUserScale = 5f

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
    ) {
        val frameWidthPx = with(density) { maxWidth.toPx() }
        val frameHeightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(frameWidthPx, frameHeightPx) {
            onFrameSizeChanged(Size(frameWidthPx, frameHeightPx))
        }

        // Escala base: la imagen SIEMPRE cubre el marco, como ContentScale.Crop
        val baseScale = max(
            frameWidthPx / bitmap.width.toFloat(),
            frameHeightPx / bitmap.height.toFloat()
        )
        val totalScale = baseScale * scale
        val displayedWpx = bitmap.width * totalScale
        val displayedHpx = bitmap.height * totalScale
        val displayedWdp: Dp = with(density) { displayedWpx.toDp() }
        val displayedHdp: Dp = with(density) { displayedHpx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, aspectRatio) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minUserScale, maxUserScale)
                        val newTotalScale = baseScale * newScale
                        val newDisplayedW = bitmap.width * newTotalScale
                        val newDisplayedH = bitmap.height * newTotalScale
                        val maxOffsetX = max(0f, (newDisplayedW - frameWidthPx) / 2f)
                        val maxOffsetY = max(0f, (newDisplayedH - frameHeightPx) / 2f)

                        onScaleChange(newScale)
                        onOffsetChange(
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(width = displayedWdp, height = displayedHdp)
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            // Rejilla guía tipo "regla de tercios", solo visual
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokePx = 1.dp.toPx()
                val guideColor = Color.White.copy(alpha = 0.5f)
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokePx)
                    val y = size.height * i / 3f
                    drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokePx)
                }
                drawRect(Color.White, size = size, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

/** Calcula, en coordenadas de píxel de la imagen ORIGINAL, qué región cae dentro del marco. */
private fun computeCropRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    userScale: Float,
    userOffset: Offset,
    frameWidthPx: Float,
    frameHeightPx: Float
): Rect {
    val baseScale = max(frameWidthPx / bitmapWidth.toFloat(), frameHeightPx / bitmapHeight.toFloat())
    val totalScale = baseScale * userScale
    val displayedW = bitmapWidth * totalScale
    val displayedH = bitmapHeight * totalScale

    // Top-left de la imagen mostrada, relativo al centro del marco (origen 0,0 = centro)
    val imageTopLeftX = -displayedW / 2f + userOffset.x
    val imageTopLeftY = -displayedH / 2f + userOffset.y

    val frameLeft = -frameWidthPx / 2f
    val frameTop = -frameHeightPx / 2f

    val cropLeftPx = (frameLeft - imageTopLeftX) / totalScale
    val cropTopPx = (frameTop - imageTopLeftY) / totalScale
    val cropWidthPx = frameWidthPx / totalScale
    val cropHeightPx = frameHeightPx / totalScale

    val left = cropLeftPx.coerceIn(0f, bitmapWidth.toFloat())
    val top = cropTopPx.coerceIn(0f, bitmapHeight.toFloat())
    val right = (cropLeftPx + cropWidthPx).coerceIn(0f, bitmapWidth.toFloat())
    val bottom = (cropTopPx + cropHeightPx).coerceIn(0f, bitmapHeight.toFloat())

    return Rect(left, top, right, bottom)
}

private fun cropBitmap(source: Bitmap, cropRect: Rect, maxDimensionPx: Int): Bitmap {
    val x = cropRect.left.toInt().coerceIn(0, source.width - 1)
    val y = cropRect.top.toInt().coerceIn(0, source.height - 1)
    val w = cropRect.width.toInt().coerceIn(1, source.width - x)
    val h = cropRect.height.toInt().coerceIn(1, source.height - y)

    val cropped = Bitmap.createBitmap(source, x, y, w, h)

    val longestSide = max(cropped.width, cropped.height)
    if (longestSide <= maxDimensionPx) return cropped

    val scaleFactor = maxDimensionPx.toFloat() / longestSide
    val newW = (cropped.width * scaleFactor).toInt().coerceAtLeast(1)
    val newH = (cropped.height * scaleFactor).toInt().coerceAtLeast(1)
    val resized = Bitmap.createScaledBitmap(cropped, newW, newH, true)
    if (resized !== cropped) cropped.recycle()
    return resized
}
