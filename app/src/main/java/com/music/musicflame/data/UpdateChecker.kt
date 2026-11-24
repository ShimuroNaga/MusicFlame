package com.music.musicflame.data

import android.content.Context
import android.os.Build
import com.music.musicflame.api.AppHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONObject

/**
 * Chequeo de actualizaciones contra la API de GitHub (releases/latest).
 *
 * - Usa el OkHttp compartido (timeouts de 15/30 s) y su caché: OkHttp guarda el ETag y manda If-None-Match solo,
 *   y una respuesta 304 no cuenta para el rate limit de GitHub.
 * - El chequeo automático (al abrir la app) se hace como máximo cada 12 h; el resto de aperturas usan la última
 *   versión conocida (guardada en [UpdatePreferences]). El manual siempre consulta (con If-None-Match).
 * - Las versiones se comparan por números (v3.25 > v3.9), no como texto.
 * - Si la release trae un APK por arquitectura, se elige el de la ABI del teléfono; si no, MusicFlame.apk o el primero.
 */
object UpdateChecker {
    private const val URL = "https://api.github.com/repos/ShimuroNaga/MusicFlame/releases/latest"
    private const val MIN_INTERVAL_MS = 12L * 60 * 60 * 1000

    class Latest(val tag: String, val downloadUrl: String)

    /** Devuelve la última versión publicada, o null si no se pudo saber (sin internet y sin dato guardado). */
    suspend fun fetch(context: Context, manual: Boolean): Latest? {
        val prefs = UpdatePreferences(context)
        val known = prefs.getKnownRelease()
        val now = System.currentTimeMillis()
        val knownLatest = if (known.tag.isNotEmpty()) Latest(known.tag, known.url) else null

        if (!manual && knownLatest != null && now - known.checkedAt in 0 until MIN_INTERVAL_MS) return knownLatest

        val fresh = withContext(Dispatchers.IO) { request(manual) }
        if (fresh == null) return if (manual) null else knownLatest
        prefs.saveKnownRelease(now, fresh.tag, fresh.downloadUrl)
        return fresh
    }

    private fun request(manual: Boolean): Latest? = try {
        val rb = Request.Builder().url(URL).header("Accept", "application/vnd.github+json")
        // "no-cache" = revalidar con If-None-Match en vez de fiarse de la copia guardada.
        if (manual) rb.cacheControl(CacheControl.FORCE_NETWORK)
        AppHttp.client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val json = JSONObject(resp.body?.string() ?: return@use null)
            val tag = json.optString("tag_name")
            if (tag.isEmpty()) return@use null
            Latest(tag, pickApkUrl(json))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun pickApkUrl(release: JSONObject): String {
        val assets = release.optJSONArray("assets") ?: return ""
        val apks = ArrayList<Pair<String, String>>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (url.endsWith(".apk")) apks += name to url
        }
        for (abi in Build.SUPPORTED_ABIS) apks.firstOrNull { it.first.contains(abi) }?.let { return it.second }
        return (apks.firstOrNull { it.first == "MusicFlame.apk" } ?: apks.firstOrNull())?.second ?: ""
    }

    /** true si [fetched] (p. ej. "v3.25") es mayor que [current] (p. ej. "v3.9"), comparando número por número. */
    fun isNewer(fetched: String, current: String): Boolean {
        val f = numbers(fetched)
        val c = numbers(current)
        if (f.isEmpty()) return false
        for (i in 0 until maxOf(f.size, c.size)) {
            val x = f.getOrElse(i) { 0 }
            val y = c.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(s: String): List<Int> =
        Regex("\\d+").findAll(s).map { it.value.toIntOrNull() ?: 0 }.toList()
}
