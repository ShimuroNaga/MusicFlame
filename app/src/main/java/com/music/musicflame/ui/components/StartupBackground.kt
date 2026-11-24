package com.music.musicflame.ui.components

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Decodifica el fondo de pantalla del usuario (imagen o primer frame de un GIF) ANTES del primer
 * frame de la app.
 *
 * Por qué existe: el fondo se dibuja con AsyncImage (Coil), que decodifica en segundo plano. Hasta
 * que termina, lo único que hay detrás es el color de fondo del tema (negro en modo oscuro), y eso
 * se veía como un destello negro de menos de 1 s al abrir la app. Esta versión ya decodificada se
 * dibuja de inmediato debajo del AsyncImage, con el mismo recorte (Crop), y se quita cuando Coil
 * termina de cargar.
 *
 * Se reduce a ~el tamaño de la pantalla (ImageDecoder respeta la orientación EXIF, igual que
 * Coil), así que es rápido y no gasta memoria de más. Devuelve null si algo falla: en ese caso
 * todo queda como antes.
 */
internal fun decodeStartupBackground(
    context: Context,
    uriString: String,
    screenWidth: Int,
    screenHeight: Int
): ImageBitmap? = try {
    val source = if (uriString.startsWith("/")) {
        ImageDecoder.createSource(File(uriString))
    } else {
        ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
    }
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Mayor reducción posible que aún cubre la pantalla completa (Crop llena ambos lados).
        val sample = maxOf(
            1,
            minOf(
                info.size.width / screenWidth.coerceAtLeast(1),
                info.size.height / screenHeight.coerceAtLeast(1)
            )
        )
        decoder.setTargetSampleSize(sample)
    }.asImageBitmap()
} catch (e: Exception) {
    null
}
