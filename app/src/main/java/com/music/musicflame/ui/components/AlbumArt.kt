package com.music.musicflame.ui.components

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.decode.DataSource
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
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

// Esquema de URI interno (solo lo entiende el Fetcher de abajo) que le dice a
// Coil "extrae la carátula directamente del archivo de audio en esta ruta",
// en vez de depender de la URI vieja de MediaStore.
private const val EMBEDDED_ART_SCHEME = "musicflame-embedded"

private fun embeddedArtUriFor(filePath: String): Uri =
    Uri.Builder().scheme(EMBEDDED_ART_SCHEME).authority("art")
        .appendQueryParameter("path", filePath)
        .build()

// ANTES: la carátula "por defecto" de cada canción se pedía SIEMPRE a
// content://media/external/audio/albumart/{albumId}, la URI vieja de
// MediaStore para artwork. Desde Android 10 (API 29) esa URI está
// deprecada: en muchos dispositivos ya no devuelve nada (o devuelve la
// carátula de OTRA canción con la que comparte albumId internamente),
// aunque el .mp3 físico sí tenga su carátula original embebida en el tag.
// Resultado: la app mostraba el ícono genérico (o una carátula ajena) en
// vez de la real, incluso sin haber tocado nada del archivo.
//
// AHORA: si la carátula pedida por la URI normal falla, y se nos pasó la
// ruta física del archivo (filePath), este Fetcher lee el tag del propio
// archivo con MediaMetadataRetriever y extrae su carátula embebida de
// verdad — sin depender del caché/índice de MediaStore.
private class EmbeddedAlbumArtFetcher(private val filePath: String) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return null
            DrawableResult(
                drawable = BitmapDrawable(android.content.res.Resources.getSystem(), bitmap),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != EMBEDDED_ART_SCHEME) return null
            val path = data.getQueryParameter("path") ?: return null
            return EmbeddedAlbumArtFetcher(path)
        }
    }
}

// ANTES: cada llamada a AlbumArt() construía su propio ImageLoader con
// remember{} (con caché de memoria/disco y cliente HTTP propios). AlbumArt()
// se usa dentro de SongItemCard, es decir, UNA VEZ POR CADA FILA de canción
// en las listas — con librerías grandes, cada fila que entraba en pantalla al
// hacer scroll o al cambiar de pantalla creaba un ImageLoader nuevo desde
// cero, lo cual se sentía como lag/micro-freezes tanto al hacer scroll como
// al navegar entre pantallas (SongScreen, AlbumScreen, PlaylistDetailScreen...
// todas recomponen sus filas visibles).
//
// AHORA: un único ImageLoader vive en este objeto y se construye UNA sola vez
// por proceso (lazy), compartiendo su caché entre todas las pantallas y
// reutilizando la misma configuración de GIFs.
private object SharedAlbumArtImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: android.content.Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    if (SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                    add(EmbeddedAlbumArtFetcher.Factory())
                }
                .build()
                .also { instance = it }
        }
    }
}

@Composable
fun AlbumArt(
    albumArtUri: String?,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    shape: AlbumArtShapeType = AlbumArtShapeType.SQUARE,
    // NUEVO: ruta física del archivo de audio (Song.path). Si se pasa, y la
    // carátula pedida por albumArtUri falla en cargar, se intenta extraer la
    // carátula embebida directamente de este archivo como respaldo (ver
    // EmbeddedAlbumArtFetcher arriba).
    filePath: String? = null
) {
    val context = LocalContext.current

    val clipShape = remember(shape, cornerRadius) { clipShapeFor(shape, cornerRadius) }

    // Reutilizamos el mismo ImageLoader compartido en vez de crear uno por fila.
    val imageLoader = remember { SharedAlbumArtImageLoader.get(context) }

    // Si la carátula pedida por albumArtUri falla en cargar (típico de la URI
    // vieja de MediaStore en Android 10+), pasamos a pedir la carátula
    // embebida directamente del archivo, si tenemos su ruta.
    var useEmbeddedFallback by remember(albumArtUri, filePath) { mutableStateOf(false) }

    val effectiveModel: Any? = when {
        albumArtUri != null && !useEmbeddedFallback -> albumArtUri
        filePath != null -> embeddedArtUriFor(filePath)
        else -> null
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
        if (effectiveModel != null) {
            SubcomposeAsyncImage(
                // Aquí le decimos qué archivo cargar y le ponemos un suavizado (crossfade)
                model = ImageRequest.Builder(context)
                    .data(effectiveModel)
                    .crossfade(true)
                    // Las formas Hexágono y Squircle usan un Outline.Generic (path),
                    // lo que obliga a Compose a recortar en un layer de software.
                    // Un hardware bitmap (el default de Coil desde Android 8+) no se
                    // puede dibujar en ese layer y la app truena con:
                    // "Software rendering doesn't support hardware bitmaps".
                    // Por eso desactivamos hardware bitmaps aquí.
                    .allowHardware(false)
                    .listener(onError = { _, _ ->
                        // Si el intento era con la URI normal (no la embebida) y
                        // tenemos ruta física, probamos una vez con la carátula
                        // embebida del archivo antes de rendirnos al ícono genérico.
                        if (!useEmbeddedFallback && filePath != null) {
                            useEmbeddedFallback = true
                        }
                    })
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
                    // No hay carátula en ningún lado (ni la URI normal ni el archivo
                    // físico tenían una): mostramos el ícono de nota musical sobre el cuadro gris
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