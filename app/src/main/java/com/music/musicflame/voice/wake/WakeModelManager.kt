package com.music.musicflame.voice.wake

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ESCUCHA SIEMPRE: los dos modelos ONNX de openWakeWord (~2,4 MB en total) ya no van dentro del APK.
 * Se descargan una sola vez, la primera vez que se usa «oye <nombre>», y se guardan en filesDir/wake/.
 * Cada archivo se verifica con tamaño + SHA-256; si algo no coincide se descarta.
 *
 * Se descargan del propio repo (models/wake/ en la rama master).
 */
object WakeModelManager {
    private const val TAG = "WakeModels"
    private const val BASE_URL = "https://raw.githubusercontent.com/ShimuroNaga/MusicFlame/master/models/wake/"

    private class Model(val name: String, val size: Long, val sha256: String)

    private val MODELS = listOf(
        Model("melspectrogram.onnx", 1_087_958L, "ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f"),
        Model("embedding_model.onnx", 1_326_578L, "70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f")
    )

    private val mutex = Mutex()

    fun melFile(context: Context): File = File(dir(context), "melspectrogram.onnx")
    fun embFile(context: Context): File = File(dir(context), "embedding_model.onnx")

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "wake")

    /** true si los dos modelos ya están descargados y completos. */
    fun isReady(context: Context): Boolean =
        MODELS.all { m -> File(dir(context), m.name).let { it.isFile && it.length() == m.size } }

    /** Descarga lo que falte. Devuelve true si los dos modelos quedaron listos. Llamar fuera del hilo principal. */
    suspend fun ensure(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isReady(context)) return@withContext true
        mutex.withLock {
            if (isReady(context)) return@withLock true
            val d = dir(context)
            if (!d.isDirectory && !d.mkdirs()) return@withLock false
            for (m in MODELS) {
                val target = File(d, m.name)
                if (target.isFile && target.length() == m.size) continue
                val ok = try {
                    download(m, target)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo descargar ${m.name}", e)
                    false
                }
                if (!ok) return@withLock false
            }
            true
        }
    }

    private suspend fun download(m: Model, target: File): Boolean {
        val part = File(target.parentFile, "${m.name}.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(BASE_URL + m.name).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "${m.name}: HTTP ${conn.responseCode}")
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
            if (hex != m.sha256 || part.length() != m.size) {
                Log.w(TAG, "${m.name}: no coincide con el SHA-256 esperado; se descarta")
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
