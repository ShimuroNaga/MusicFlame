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
import androidx.compose.runtime.LaunchedEffect
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
import com.music.musicflame.data.ArtworkCacheRepository
import com.music.musicflame.data.ArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

// `internal` (no `private`) a propósito: FullScreenPlayer.kt reutiliza esta
// misma Uri "empaquetada" para pintar la carátula real por canción en el
// pager, en vez de duplicar esta lógica con su propio esquema (ver el bug de
// "una sola carátula para las N canciones agrupadas en el mismo álbum",
// comentado más abajo en AlbumArt()).
internal fun embeddedArtUriFor(filePath: String): Uri =
    Uri.Builder().scheme(EMBEDDED_ART_SCHEME).authority("art")
        .appendQueryParameter("path", filePath)
        .build()

// Lee la carátula embebida (tag ID3/APIC) directamente del archivo de audio
// en filePath, usando MediaMetadataRetriever. Se usa tanto como PRIMERA
// opción para carátulas "de fábrica" como de último recurso para carátulas
// personalizadas que fallan al cargar (ver comentario grande en AlbumArt()
// más abajo, y el fix del bug de carátulas repetidas por álbum).
private class EmbeddedAlbumArtFetcher(private val filePath: String) : Fetcher {
    // Algunos archivos (descargados, corruptos, con codecs raros) pueden hacer que
    // MediaMetadataRetriever se quede pegado un buen rato en vez de fallar rápido.
    // Antes eso significaba que la carátula se quedaba "cargando" indefinidamente
    // y nunca caía al ícono genérico. Con este tope de 2.5s, si no responde a
    // tiempo se trata igual que si no tuviera carátula: cae al siguiente fallback
    // (o al ícono) en vez de quedarse trabada.
    override suspend fun fetch(): FetchResult? = withTimeoutOrNull(2500) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                val art = retriever.embeddedPicture ?: return@withContext null
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return@withContext null
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
// `internal` (no `private`) a propósito: FullScreenPlayer.kt reutiliza este
// mismo ImageLoader (y su Fetcher de carátula embebida) para el pager de
// carátulas, en vez de crear un ImageLoader propio sin ese fallback.
internal object SharedAlbumArtImageLoader {
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
    // NUEVO: ruta física del archivo de audio (Song.path). Se usa para leer
    // la carátula embebida directamente del archivo (ver más abajo).
    filePath: String? = null,
    // NUEVO: true cuando `albumArtUri` es una carátula elegida a mano por el
    // usuario (Song.hasCustomCover / Album.albumArtIsCustom), y no la URI
    // "de fábrica" de MediaStore por álbum. Los llamadores deben pasar este
    // flag; por defecto es false (URI de fábrica).
    isCustomCover: Boolean = false
) {
    val context = LocalContext.current

    val clipShape = remember(shape, cornerRadius) { clipShapeFor(shape, cornerRadius) }

    // Reutilizamos el mismo ImageLoader compartido en vez de crear uno por fila.
    val imageLoader = remember { SharedAlbumArtImageLoader.get(context) }

    // NUEVO: cache persistente de qué fuente de carátula funcionó (o si ninguna)
    // la última vez para este archivo. Evita repetir intentos que ya sabemos
    // que van a fallar cada vez que se abre la lista (ver ArtworkCacheRepository).
    val artworkCache = remember { ArtworkCacheRepository(context) }
    val cachedSource = remember(filePath, isCustomCover) {
        if (!isCustomCover && filePath != null) artworkCache.get(filePath) else null
    }

    // BUG ARREGLADO: cuando varias canciones se agrupan en un mismo álbum
    // (mismo nombre+artista), Android les asigna internamente el mismo
    // ALBUM_ID en MediaStore. La URI "de fábrica"
    // content://media/external/audio/albumart/{albumId} devuelve, para TODAS
    // esas canciones, UN SOLO bitmap "representante" del álbum (el que el
    // escáner haya tomado de una sola de ellas) — y esa carga NO falla, solo
    // trae la imagen equivocada. Como antes solo caíamos a la carátula
    // embebida del archivo cuando la carga fallaba con error, ese fallback
    // nunca se activaba en este caso: las canciones agrupadas terminaban
    // mostrando todas la misma carátula ajena, aunque cada .mp3 físico
    // tuviera su propia carátula original correcta en el tag ID3.
    //
    // AHORA: si NO es una carátula personalizada por el usuario y tenemos la
    // ruta física del archivo, se intenta PRIMERO la carátula embebida real
    // de ESA canción (MediaMetadataRetriever sobre su propio archivo). Solo
    // si ese archivo no trae ninguna carátula propia caemos a la URI
    // genérica por álbum como último recurso. Las carátulas personalizadas
    // (isCustomCover = true) se siguen respetando primero, como antes.
    //
    // Los valores iniciales ahora arrancan directo desde el cache cuando ya
    // sabemos el resultado (ver arriba): si la última vez no había carátula
    // en ningún lado, arranca directo en "agotado" (ícono al instante, sin
    // tocar disco); si la última vez funcionó la Uri de álbum, se salta el
    // intento de carátula embebida (que ya sabemos que falla) y va directo
    // a esa Uri.
    var useEmbeddedFallback by remember(albumArtUri, filePath, isCustomCover) { mutableStateOf(false) }
    var useAlbumUriFallback by remember(albumArtUri, filePath, isCustomCover) {
        mutableStateOf(cachedSource == ArtworkSource.ALBUM_URI)
    }
    // NUEVO: cuando el ÚLTIMO escalón de fallback (la URI genérica de MediaStore
    // por álbum) también falla, antes no pasaba nada: el `when` de onError no
    // matcheaba ninguna condición (los dos flags ya estaban en true) y la
    // carátula se quedaba en el estado de error/carga de Coil sin forzar nunca
    // el ícono de nota musical. En pantallas como SongScreen, donde muchas
    // canciones no tienen carátula real (ni embebida ni en MediaStore), eso se
    // veía como un cuadro vacío en vez del ícono que sí aparecía en otras
    // pantallas. Ahora, al agotarse los dos intentos, forzamos effectiveModel
    // a null explícitamente para caer siempre en la rama `else` de abajo (un
    // Box simple con el ícono, sin depender de que Coil reporte bien su
    // estado de error).
    var exhaustedAllFallbacks by remember(albumArtUri, filePath, isCustomCover) {
        mutableStateOf(cachedSource == ArtworkSource.NONE)
    }

    val effectiveModel: Any? = when {
        exhaustedAllFallbacks -> null
        isCustomCover && albumArtUri != null && !useEmbeddedFallback -> albumArtUri
        filePath != null && !useAlbumUriFallback -> embeddedArtUriFor(filePath)
        !isCustomCover && albumArtUri != null -> albumArtUri
        else -> null
    }

    // Guarda en el cache, apenas se sabe con certeza, si ninguna fuente tuvo
    // carátula — así la próxima vez ni se intenta. No se guarda nada para
    // carátulas personalizadas (isCustomCover), esas no pasan por este cache.
    LaunchedEffect(exhaustedAllFallbacks, filePath, isCustomCover) {
        if (exhaustedAllFallbacks && !isCustomCover && filePath != null) {
            artworkCache.set(filePath, ArtworkSource.NONE)
        }
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
                    .listener(
                        onSuccess = { _, _ ->
                            // Guarda en cache cuál fuente funcionó, para que la próxima
                            // vez se vaya directo a ella sin repetir intentos.
                            if (!isCustomCover && filePath != null) {
                                val source = if (effectiveModel is Uri && effectiveModel.scheme == EMBEDDED_ART_SCHEME) {
                                    ArtworkSource.EMBEDDED
                                } else {
                                    ArtworkSource.ALBUM_URI
                                }
                                artworkCache.set(filePath, source)
                            }
                        },
                        onError = { _, _ ->
                        // Avanza al siguiente escalón del fallback según cuál
                        // intento fue el que falló (ver comentario arriba).
                        when {
                            isCustomCover && !useEmbeddedFallback -> useEmbeddedFallback = true
                            !useAlbumUriFallback -> useAlbumUriFallback = true
                            else -> exhaustedAllFallbacks = true
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
                    // Mientras carga (Loading): mismo ícono y mismo tono que el estado
                    // Error/sin-carátula de abajo (antes tenía 50% de opacidad y el otro
                    // 100%, así que se veía "apagado" mientras cargaba y de golpe se
                    // ponía más marcado al resolver — inconsistente sin razón real).
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