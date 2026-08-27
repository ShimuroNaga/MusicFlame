package com.music.musicflame.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracción de color desde una imagen elegida por el usuario en la galería
 * (catálogo de personalizaciones: botón "Elegir color de una imagen" en el
 * diálogo "Color del ecualizador", modo "Personalizado"). Es una forma más de
 * llegar a un color, alternativa al selector RGBA manual — el resultado se
 * guarda igual que cualquier otro color personalizado (mismo hex, mismo
 * SharedPreferences), esto solo decide DE DÓNDE sale ese hex.
 *
 * Se corre entero fuera del hilo principal: decodificar un bitmap de galería
 * y correr Palette sobre él no son operaciones instantáneas.
 */

// Tope de lado más largo al decodificar: una foto de galería puede pesar
// varios miles de píxeles de lado, y Palette de por sí solo necesita una
// versión chica para generar sus swatches. Bajar la resolución ANTES de
// decodificar (con inSampleSize) evita cargar el bitmap gigante completo en
// memoria solo para tirarlo después.
private const val MAX_DECODE_DIMENSION = 512

/**
 * Decodifica la imagen en [uri] ya reducida a un tamaño manejable (ver
 * [MAX_DECODE_DIMENSION]), sin cargar primero la imagen a tamaño completo.
 * Devuelve null si la imagen no pudo leerse o decodificarse.
 */
private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver

    // Primera pasada: solo medir (inJustDecodeBounds), no carga píxeles.
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null
    } catch (e: Exception) {
        return null
    }

    val (width, height) = boundsOptions.outWidth to boundsOptions.outHeight
    if (width <= 0 || height <= 0) return null

    var sampleSize = 1
    while ((width / sampleSize) > MAX_DECODE_DIMENSION || (height / sampleSize) > MAX_DECODE_DIMENSION) {
        sampleSize *= 2
    }

    // Segunda pasada: decodificar de verdad, ya con el sampleSize calculado.
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return try {
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Extrae un color representativo de la imagen en [uri] y lo devuelve como
 * hex "#RRGGBB", listo para guardarse como color personalizado (mismo
 * formato que acepta [com.music.musicflame.ui.theme.parseCustomTextColor]).
 *
 * Orden de preferencia de swatch: Vibrant > DarkVibrant > LightVibrant >
 * Dominant > Muted — la primera que Palette logre generar para esa imagen,
 * para que casi siempre salga un color "vivo" en vez de un gris apagado.
 *
 * Devuelve null si la imagen no pudo leerse/decodificarse o si Palette no
 * pudo generar ningún swatch utilizable.
 */
suspend fun extractColorHexFromImage(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
    val bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri) } ?: return@withContext null

    val palette = try {
        Palette.from(bitmap).generate()
    } catch (e: Exception) {
        null
    } finally {
        bitmap.recycle()
    }

    val swatch = palette?.vibrantSwatch
        ?: palette?.darkVibrantSwatch
        ?: palette?.lightVibrantSwatch
        ?: palette?.dominantSwatch
        ?: palette?.mutedSwatch

    swatch?.let { String.format("#%06X", 0xFFFFFF and it.rgb) }
}
