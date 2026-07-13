package com.music.musicflame.ui.components

import android.os.Build.VERSION.SDK_INT
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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

// Forma de rombo (diamante) que se ajusta al tamaño exacto del contenedor.
private class DiamondShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        return Outline.Generic(path)
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

    val clipShape = remember(shape, cornerRadius) {
        when (shape) {
            AlbumArtShapeType.CIRCLE -> CircleShape
            AlbumArtShapeType.DIAMOND -> DiamondShape()
            AlbumArtShapeType.SQUARE -> RoundedCornerShape(cornerRadius)
        }
    }

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

    Box(
        modifier = Modifier
            .size(size)
            .clip(clipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            SubcomposeAsyncImage(
                // Aquí le decimos qué archivo cargar y le ponemos un suavizado (crossfade)
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(true)
                    .build(),
                // ¡AQUÍ ES DONDE CONECTAMOS EL DECODIFICADOR DE GIFS!
                imageLoader = imageLoader,
                contentDescription = "Carátula",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            ) {
                val painterState = painter.state
                if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                    // Se encontró la carátula: mostramos la imagen real
                    SubcomposeAsyncImageContent()
                } else if (painterState is coil.compose.AsyncImagePainter.State.Error) {
                    // No hay carátula (o falló la URI heredada de MediaStore en Android 10+):
                    // mostramos el ícono de nota musical sobre el cuadro gris
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(size / 2),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Mientras carga (Loading) no mostramos nada: se ve el fondo gris de la Box
            }
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}