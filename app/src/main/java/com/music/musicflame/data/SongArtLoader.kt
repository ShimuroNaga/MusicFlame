package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache

/**
 * RAÍZ DEL BUG: cuando una canción NO tiene carátula elegida a mano, el resto
 * de la app (Song.albumArtUri) apunta a la URI "de fábrica" de MediaStore
 * (content://media/external/audio/albumart/{albumId}). Esa URI quedó
 * deprecada desde Android 10 (scoped storage): en la mayoría de celulares
 * modernos ya no hay ningún archivo cacheado detrás de ella, así que abrirla
 * simplemente no devuelve nada.
 *
 * La pantalla en vivo (AlbumArt.kt / Coil) no sufre esto porque ya tiene su
 * propio fallback: si esa URI falla, prueba sacar la carátula embebida REAL
 * del archivo con MediaMetadataRetriever. Pero la notificación de medios
 * (Media3/MediaSession) y los widgets de home screen arman su bitmap por su
 * cuenta, sin ese fallback, así que ahí sí se ve sin carátula aunque el
 * .mp3 físico tenga su carátula real embebida en el tag ID3.
 *
 * Este objeto centraliza esa misma lógica de fallback para que la
 * notificación (SongArtBitmapLoader) y los widgets (MusicFlameWidgetProvider,
 * MusicFlameVinylWidgetProvider) la compartan en vez de duplicarla.
 */
object SongArtLoader {

    // Esquema propio para "empaquetar" en una sola Uri tanto la ruta física
    // del archivo (de donde sacar la carátula embebida real) como la URI
    // genérica de MediaStore a la que caer si ese archivo no trae ninguna.
    const val EMBEDDED_ART_SCHEME = "musicflame-embedded-art"

    // Lado máximo (px) al que se decodifica; suficiente para notificación/widgets y evita cargar
    // carátulas de 3000x3000 (~36 MB) cada vez que cambia la canción.
    private const val MAX_DIM = 768

    // Caché en memoria (LRU por tamaño): la notificación y los widgets pedían la misma carátula
    // una y otra vez (cada actualización = abrir el archivo + MediaMetadataRetriever + decode).
    private val artCache = object : LruCache<String, Bitmap>(cacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun cacheKb(): Int =
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(4 * 1024, 24 * 1024)

    /** Descarta lo cacheado (p. ej. tras escribir una carátula nueva en el archivo). */
    fun clearCache() = artCache.evictAll()
    private const val PARAM_PATH = "path"
    private const val PARAM_FALLBACK = "fallback"

    /**
     * Arma la Uri "empaquetada" para una canción sin carátula personalizada.
     * fallbackUri es opcional (la URI genérica de MediaStore, por si el
     * archivo no trae carátula embebida propia).
     */
    fun embeddedArtUri(filePath: String, fallbackUri: String?): Uri {
        val builder = Uri.Builder()
            .scheme(EMBEDDED_ART_SCHEME)
            .authority("art")
            .appendQueryParameter(PARAM_PATH, filePath)
        if (!fallbackUri.isNullOrEmpty()) {
            builder.appendQueryParameter(PARAM_FALLBACK, fallbackUri)
        }
        return builder.build()
    }

    /** Sobrecarga cómoda para cuando solo se tiene el String de la Uri. */
    fun loadBitmap(context: Context, artUriString: String?): Bitmap? {
        if (artUriString.isNullOrEmpty()) return null
        return try {
            loadBitmap(context, Uri.parse(artUriString))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Carga (de forma síncrona/bloqueante; llamar siempre desde un hilo de
     * fondo) el bitmap de carátula para la Uri dada, sea del esquema
     * "empaquetado" de este objeto o una Uri normal (content://, file://).
     * Devuelve null si no se pudo obtener ninguna imagen.
     */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val key = uri.toString()
        artCache.get(key)?.let { cached -> if (!cached.isRecycled) return cached }
        val loaded = loadBitmapUncached(context, uri)
        if (loaded != null) artCache.put(key, loaded)
        return loaded
    }

    private fun loadBitmapUncached(context: Context, uri: Uri): Bitmap? {
        if (uri.scheme == EMBEDDED_ART_SCHEME) {
            val path = uri.getQueryParameter(PARAM_PATH)
            val fallback = uri.getQueryParameter(PARAM_FALLBACK)

            val embedded = path?.let { extractEmbeddedArt(it) }
            if (embedded != null) return embedded

            return if (!fallback.isNullOrEmpty()) {
                loadFromContentOrFileUri(context, Uri.parse(fallback))
            } else {
                null
            }
        }

        return loadFromContentOrFileUri(context, uri)
    }

    private fun loadFromContentOrFileUri(context: Context, uri: Uri): Bitmap? {
        return when (uri.scheme) {
            "content", "file" -> try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { decodeSampled(it) }
            } catch (e: Exception) {
                null
            }
            else -> null
        }
    }

    /** Misma técnica que EmbeddedAlbumArtFetcher en AlbumArt.kt (tag ID3/APIC vía MediaMetadataRetriever). */
    private fun extractEmbeddedArt(filePath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture ?: return null
            decodeSampled(art)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // no-op
            }
        }
    }

    /** Decodifica reducida (inSampleSize potencia de 2) para que el lado mayor quede >= MAX_DIM. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
