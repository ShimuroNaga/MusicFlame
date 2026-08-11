package com.music.musicflame.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Resultado devuelto por lrclib.net. `syncedLyrics` viene en formato LRC
 * (con marcas de tiempo por línea); `plainLyrics` es texto plano sin sincronizar.
 */
data class LrcLibResult(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

interface LyricsApiService {
    // Búsqueda exacta: usada cuando conocemos título + artista + duración aproximada.
    @GET("api/get")
    suspend fun get(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null
    ): retrofit2.Response<LrcLibResult>

    // Búsqueda libre: usada cuando el usuario escribe manualmente qué buscar.
    @GET("api/search")
    suspend fun search(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String? = null
    ): retrofit2.Response<List<LrcLibResult>>
}

object LyricsApi {
    val service: LyricsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsApiService::class.java)
    }
}
