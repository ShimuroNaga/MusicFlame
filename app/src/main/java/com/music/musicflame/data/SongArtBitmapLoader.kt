package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * BitmapLoader que le pasamos a MediaSession.Builder para que la notificación
 * (y cualquier MediaController conectado) sepa resolver la Uri "empaquetada"
 * de SongArtLoader además de las Uris normales.
 *
 * Sin esto, Media3 usa su BitmapLoader por defecto, que solo abre la Uri tal
 * cual llega en MediaMetadata.artworkUri: si esa Uri es la genérica de
 * MediaStore por álbum (deprecada en Android 10+), la carga falla en
 * silencio y la notificación queda sin carátula, aunque el archivo .mp3 sí
 * tenga su carátula real embebida.
 *
 * NOTA: en media3 1.3.1 (la versión que usa este proyecto) todavía no existe
 * la clase de conveniencia SimpleBitmapLoader (llegó en una versión más
 * nueva), así que implementamos la interfaz BitmapLoader directamente.
 */
@OptIn(UnstableApi::class)
class SongArtBitmapLoader(private val context: Context) : BitmapLoader {

    // Un solo hilo de fondo alcanza: la notificación solo pide releer la
    // carátula cuando cambia la canción, nunca en paralelo a alto volumen.
    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return Futures.submit(
            Callable {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IllegalStateException("No se pudo decodificar los bytes de la imagen")
            },
            executor
        )
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return Futures.submit(
            Callable {
                SongArtLoader.loadBitmap(context, uri)
                    ?: throw IllegalStateException("No se pudo cargar la carátula desde $uri")
            },
            executor
        )
    }
}
