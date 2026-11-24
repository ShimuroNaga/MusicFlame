package com.music.musicflame.api

import android.os.SystemClock

/**
 * Búsqueda de YouTube con caché en memoria: LRU de 40 consultas, TTL de 20 min, con la consulta normalizada
 * (sin mayúsculas ni espacios de más) para que "Bad  Bunny" y "bad bunny" cuenten como una sola. Cada
 * search.list gasta 100 unidades de la cuota diaria (10.000), así que repetir una búsqueda no debe costar nada.
 *
 * Si la corrutina se cancela (el usuario siguió escribiendo), Retrofit cancela la petición en vuelo.
 */
object YouTubeRepository {
    private const val MAX_ENTRIES = 40
    private const val TTL_MS = 20 * 60_000L

    private class Entry(val at: Long, val response: YouTubeResponse)

    private val cache = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    fun normalize(query: String): String = query.trim().lowercase().replace(Regex("\\s+"), " ")

    suspend fun search(query: String): YouTubeResponse {
        val key = normalize(query)
        val now = SystemClock.elapsedRealtime()
        synchronized(cache) {
            cache[key]?.takeIf { now - it.at < TTL_MS }?.let { return it.response }
        }
        val response = RetrofitClient.instance.searchVideos(query = key)
        synchronized(cache) { cache[key] = Entry(now, response) }
        return response
    }
}
