package com.music.musicflame.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Escribe de verdad título, artista, álbum y carátula en los metadatos del
 * archivo de audio en disco (a diferencia de SongCustomizationRepository,
 * que solo guarda la personalización dentro de la app).
 *
 * Se activa/desactiva con el switch "Guardar etiquetas reales en el archivo"
 * de Ajustes > Canciones (ver SettingsRepository.isRealTagWritingEnabled).
 * Requiere el permiso "Acceso a todos los archivos" (MANAGE_EXTERNAL_STORAGE),
 * ya declarado en el Manifest.
 *
 * IMPORTANTE — no corrompe el audio: usamos JAudioTagger (fork de Kaned1as
 * para Android), que solo reescribe el bloque de metadatos propio de cada
 * contenedor y deja los datos de audio intactos, sin re-codificar nada.
 *
 * FORMATOS SOPORTADOS: mp3, flac, ogg, wav, m4a, wma, aiff, dsf y opus (todos
 * los que la librería sabe re-etiquetar). El único formato que la app puede
 * reproducir y que se queda FUERA es .aac "crudo" (elemental, sin contenedor
 * MP4): ese formato no tiene un lugar estándar donde guardar metadatos, así
 * que ningún tagger puede escribirle tags de forma confiable. Para esos
 * archivos la edición sigue funcionando como antes, solo dentro de la app.
 */
object RealTagWriter {

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "flac", "ogg", "oga", "wav", "m4a", "m4b", "wma", "aiff", "aif", "dsf", "opus"
    )

    fun isSupportedFile(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    /** En minSdk 31+ esto SIEMPRE requiere MANAGE_EXTERNAL_STORAGE concedido. */
    fun hasFileAccessPermission(): Boolean = Environment.isExternalStorageManager()

    /** Abre la pantalla del sistema para conceder "Acceso a todos los archivos". */
    fun requestFileAccessPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                // Si ni siquiera esta pantalla existe (fabricante raro), no hay más que hacer.
            }
        }
    }

    /**
     * Aplica título/artista/álbum/carátula al archivo real de [path].
     * Cualquier parámetro en null se deja tal cual estaba en el archivo.
     * Devuelve true si se escribió con éxito; false si no se hizo nada (por
     * falta de permiso, formato no soportado, o error al escribir).
     */
    fun applyTags(
        context: Context,
        path: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        coverUri: Uri? = null
    ): Boolean {
        if (!isSupportedFile(path)) return false
        if (!hasFileAccessPermission()) return false
        if (title == null && artist == null && album == null && coverUri == null) return false

        val file = File(path)
        if (!file.exists() || !file.canRead()) return false

        return try {
            backupOriginalIfNeeded(context, file)

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            title?.let { tag.setField(FieldKey.TITLE, it) }
            artist?.let { tag.setField(FieldKey.ARTIST, it) }
            album?.let { tag.setField(FieldKey.ALBUM, it) }

            coverUri?.let { uri ->
                val jpegBytes = uriToJpegBytes(context, uri)
                if (jpegBytes != null) {
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = jpegBytes
                    artwork.mimeType = "image/jpeg"
                    try { tag.deleteArtworkField() } catch (e: Exception) { /* puede no haber ninguna */ }
                    tag.setField(artwork)
                }
            }

            audioFile.commit()

            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Antes de tocar un archivo por primera vez, guarda una copia de respaldo
     * en almacenamiento privado de la app (Android/data/.../files/tag_backups),
     * por si algo sale mal. Nunca sobreescribe un respaldo ya existente.
     */
    private fun backupOriginalIfNeeded(context: Context, file: File) {
        try {
            val backupDir = File(context.getExternalFilesDir(null), "tag_backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val backupFile = File(backupDir, file.name)
            if (!backupFile.exists()) {
                file.copyTo(backupFile, overwrite = false)
            }
        } catch (e: Exception) {
            // El respaldo es un extra de seguridad; si falla no debe bloquear el guardado real.
        }
    }

    /** Decodifica una content:// Uri de imagen (o GIF, tomando su primer cuadro) a JPEG. */
    private fun uriToJpegBytes(context: Context, uri: Uri, maxDimension: Int = 1000, quality: Int = 90): ByteArray? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val bitmap = scaleDown(original, maxDimension)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
