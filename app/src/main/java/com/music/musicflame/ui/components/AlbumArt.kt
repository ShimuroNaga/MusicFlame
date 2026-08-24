package com.music.musicflame.ui.components

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.music.musicflame.AlbumArtShapeType
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// Hexágono regular inscrito en el tamaño exacto del contenedor (punta arriba y abajo).
private class HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}

// Squircle: superelipse (|x|^n + |y|^n = 1) que da esquinas con curvatura continua,
// más suave que un simple RoundedCornerShape.
private class SquircleShape(private val n: Double = 4.0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val steps = 72
        val path = Path()
        for (i in 0..steps) {
            val t = (i.toDouble() / steps) * 2 * Math.PI
            val cosT = cos(t)
            val sinT = sin(t)
            val x = (sign(cosT) * kotlin.math.abs(cosT).pow(2.0 / n)) * cx + cx
            val y = (sign(sinT) * kotlin.math.abs(sinT).pow(2.0 / n)) * cy + cy
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

// Devuelve la Shape de recorte que corresponde a cada estilo de carátula.
// Centralizado aquí para que AlbumArt() y la vista previa del selector de Ajustes
// usen exactamente la misma geometría.
private fun clipShapeFor(shape: AlbumArtShapeType, cornerRadius: Dp): Shape = when (shape) {
    AlbumArtShapeType.CIRCLE -> CircleShape
    AlbumArtShapeType.VINYL -> CircleShape
    AlbumArtShapeType.HEXAGON -> HexagonShape()
    AlbumArtShapeType.SQUIRCLE -> SquircleShape()
    AlbumArtShapeType.SQUARE -> RoundedCornerShape(cornerRadius)
}

// Surcos finos + hoyo central del disco de vinilo, dibujados encima del contenido.
@Composable
private fun VinylOverlay(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val grooveColor = Color.Black.copy(alpha = 0.18f)
        listOf(0.62f, 0.75f, 0.88f).forEach { fraction ->
            drawCircle(
                color = grooveColor,
                radius = radius * fraction,
                center = center,
                style = Stroke(width = radius * 0.02f)
            )
        }
        // Hoyo central del disco
        drawCircle(
            color = Color.Black.copy(alpha = 0.85f),
            radius = radius * 0.14f,
            center = center
        )
    }
}

// Miniatura minimalista usada en el selector "Forma de la carátula" de Ajustes > Apariencia:
// un swatch de color sólido recortado a la forma real, para que el usuario la vea, no la lea.
@Composable
fun AlbumArtShapePreview(
    shape: AlbumArtShapeType,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val clipShape = remember(shape) { clipShapeFor(shape, size / 5) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(clipShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (shape == AlbumArtShapeType.VINYL) {
            VinylOverlay(size)
        }
    }
}

@Composable
fun AlbumArt(
    albumArtUri: String?,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    shape: AlbumArtShapeType = AlbumArtShapeType.SQUARE
) {
    val context = LocalContext.current

    val clipShape = remember(shape, cornerRadius) { clipShapeFor(shape, cornerRadius) }

    // Configuramos el lector de GIFs y lo guardamos en caché con 'remember'
    // para no ralentizar la aplicación.
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // El fondo gris (surfaceVariant) solo se pinta cuando hace falta como placeholder
    // (cargando, sin carátula o error). Antes se pintaba SIEMPRE detrás de la Box entera,
    // lo que dejaba un fino borde gris asomando por el anti-aliasing del recorte redondeado,
    // incluso con la imagen ya cargada. Ahora, si la imagen carga bien, no hay ningún
    // fondo detrás de ella: nada que pueda asomar por el borde.
    Box(
        modifier = Modifier
            .size(size)
            .clip(clipShape),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            SubcomposeAsyncImage(
                // Aquí le decimos qué archivo cargar y le ponemos un suavizado (crossfade)
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(true)
                    // Las formas Hexágono y Squircle usan un Outline.Generic (path),
                    // lo que obliga a Compose a recortar en un layer de software.
                    // Un hardware bitmap (el default de Coil desde Android 8+) no se
                    // puede dibujar en ese layer y la app truena con:
                    // "Software rendering doesn't support hardware bitmaps".
                    // Por eso desactivamos hardware bitmaps aquí.
                    .allowHardware(false)
                    .build(),
                // ¡AQUÍ ES DONDE CONECTAMOS EL DECODIFICADOR DE GIFS!
                imageLoader = imageLoader,
                contentDescription = "Carátula",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            ) {
                val painterState = painter.state
                if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                    // Se encontró la carátula: mostramos la imagen real, sin fondo gris detrás
                    SubcomposeAsyncImageContent()
                } else if (painterState is coil.compose.AsyncImagePainter.State.Error) {
                    // No hay carátula (o falló la URI heredada de MediaStore en Android 10+):
                    // mostramos el ícono de nota musical sobre el cuadro gris
                    Box(
                        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(size / 2),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Mientras carga (Loading): fondo gris de placeholder
                    Box(modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        } else {
            Box(
                modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(size / 2),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detalle de disco de vinilo: surcos finos + hoyo central, dibujados encima de la carátula.
        if (shape == AlbumArtShapeType.VINYL) {
            VinylOverlay(size)
        }
    }
}