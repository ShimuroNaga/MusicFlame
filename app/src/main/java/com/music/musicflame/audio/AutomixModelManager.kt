package com.music.musicflame.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * AUTOMIX: descarga (una sola vez) el modelo ONNX "Beat This!" (small0, 9,5 MB, licencia MIT) que usa
 * BeatWeave para detectar beats. BeatWeave NUNCA descarga nada por su cuenta: la app le entrega los bytes.
 *
 * Se guarda en filesDir/beatweave/ y se verifica con SHA-256 (el mismo que BeatWeave exige después).
 * Si la descarga falla (sin red, etc.) se reintenta, como máximo, cada 10 minutos.
 */
object AutomixModelManager {
    private const val TAG = "AutomixModel"

    private const val MODEL_FILE_NAME = "beat-this-small0.onnx"
    private const val MODEL_SIZE_BYTES = 9_470_458L
    private const val MODEL_SHA256 = "b6d54bca156b039593b6d9d48fd3ab3e5d09be06bbbe2706f0376c9f103d5191"
    private const val MODEL_URL =
        "https://github.com/adrielGGmotion/BeatWeave/releases/download/v0.10.0/beat-this-small0.onnx"

    private const val RETRY_AFTER_FAILURE_MS = 10 * 60_000L

    private val mutex = Mutex()
    @Volatile private var lastFailureAt = 0L

    /** Motivo del último fallo de descarga (para mostrarlo en Ajustes). */
    @Volatile var lastError: String = ""
        private set

    fun modelFile(context: Context): File =
        File(File(context.applicationContext.filesDir, "beatweave"), MODEL_FILE_NAME)

    /** true si el modelo ya está descargado y completo. */
    fun isReady(context: Context): Boolean {
        val f = modelFile(context)
        return f.isFile && f.length() == MODEL_SIZE_BYTES
    }

    /** Devuelve el archivo del modelo (descargándolo si hace falta) o null si no se pudo conseguir. */
    suspend fun ensureModel(context: Context): File? = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        if (isReady(context)) return@withContext target
        mutex.withLock {
            if (isReady(context)) return@withLock target
            val now = SystemClock.elapsedRealtime()
            if (lastFailureAt != 0L && now - lastFailureAt < RETRY_AFTER_FAILURE_MS) {
                AutomixStatus.set("Modelo no disponible ($lastError). Se reintenta en unos minutos")
                return@withLock null
            }
            AutomixStatus.set("Descargando el modelo de AutoMix (9,5 MB)…")
            val ok = try {
                download(target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar el modelo de AutoMix", e)
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                false
            }
            if (ok) {
                lastFailureAt = 0L
                AutomixStatus.set("Modelo de AutoMix listo")
                target
            } else {
                lastFailureAt = SystemClock.elapsedRealtime()
                AutomixStatus.set("No se pudo descargar el modelo: $lastError")
                null
            }
        }
    }

    private suspend fun download(target: File): Boolean {
        val dir = target.parentFile ?: return false
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val part = File(dir, "$MODEL_FILE_NAME.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Descarga del modelo: HTTP ${conn.responseCode}")
                lastError = "HTTP ${conn.responseCode}"
                return false
            }
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex != MODEL_SHA256 || part.length() != MODEL_SIZE_BYTES) {
                Log.w(TAG, "El modelo descargado no coincide con el SHA-256 esperado; se descarta")
                lastError = "el archivo descargado está corrupto (SHA-256)"
                return false
            }
            if (target.exists()) target.delete()
            return part.renameTo(target)
        } finally {
            conn?.disconnect()
            if (part.exists()) part.delete()
        }
    }
}

/**
 * AUTOMIX: último mensaje de estado (descarga del modelo, canción en análisis, errores). Lo muestra
 * Ajustes > Canciones y también va al Logcat con la etiqueta "AutomixStatus".
 */
object AutomixStatus {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    fun set(message: String) {
        Log.i("AutomixStatus", message)
        _text.value = message
    }
}
