package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
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
                // ARREGLO icono personalizado en notificación: antes, si la
                // canción no traía carátula real, SongArtLoader.loadBitmap
                // devolvía null y esto lo convertía en una excepción. Cuando
                // esa excepción reventaba el Future, Media3 nunca llegaba a
                // llamar builder.setLargeIcon(...) y el sistema rellenaba el
                // hueco solo con el icono "de fábrica" del manifest — el que
                // NO cambia con el selector de Ajustes (ver AppIconManager),
                // por eso la notificación siempre mostraba el logo original
                // de MusicFlame sin importar qué icono personalizado
                // (RemixFlame/DemonMusic/etc) tuvieras activo. Ahora, en vez
                // de tirar la excepción, el respaldo final es el bitmap del
                // icono que el usuario tiene elegido ahora mismo.
                SongArtLoader.loadBitmap(context, uri)
                    ?: loadSelectedAppIconBitmap()
            },
            executor
        )
    }

    private fun loadSelectedAppIconBitmap(): Bitmap {
        val iconRes = AppIconManager.getSelectedIconDrawableRes(context)
        val drawable = ContextCompat.getDrawable(context, iconRes)
            ?: throw IllegalStateException("No se pudo cargar el icono de la app ($iconRes) como respaldo de carátula")

        // Camino simple: ícono plano de toda la vida (APIs sin icono
        // adaptativo), se usa el bitmap tal cual.
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        // Ícono adaptativo (AdaptiveIconDrawable, API 26+): lo "aplanamos"
        // dibujándolo sobre un canvas cuadrado de su tamaño intrínseco. No
        // aplica la máscara circular/squircle del launcher (eso lo decide
        // cada launcher, no este código), pero para el respaldo de carátula
        // de la notificación el resultado se ve correcto igual.
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
