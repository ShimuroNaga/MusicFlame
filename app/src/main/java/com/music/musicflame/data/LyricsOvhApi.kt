package com.music.musicflame.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Respaldo de "otras plataformas" cuando lrclib.net no tiene la canción.
 * lyrics.ovh es gratis y sin API key; por debajo combina Genius, AZLyrics,
 * Paroles.net, LyricsMania, Letras.mus.br y Lyrics.com, así que probarlo
 * cubre bastantes más canciones que solo lrclib. Solo trae texto plano
 * (sin sincronización por tiempo), pero es mejor que nada.
 */
data class LyricsOvhResult(
    val lyrics: String? = null,
    val error: String? = null
)

/** Resultado de /suggest, que en realidad busca en Deezer por texto libre (guiado por título). */
data class DeezerArtist(val name: String? = null)
data class DeezerTrack(
    val title: String? = null,
    val title_short: String? = null,
    val artist: DeezerArtist? = null
)
data class DeezerSuggestResponse(val data: List<DeezerTrack>? = null)

interface LyricsOvhApiService {
    // Requiere artista + título exactos (o al menos razonablemente parecidos).
    @GET("v1/{artist}/{title}")
    suspend fun getLyrics(
        @Path("artist") artist: String,
        @Path("title") title: String
    ): retrofit2.Response<LyricsOvhResult>

    // Búsqueda libre guiada por texto (normalmente el título): no exige artista.
    @GET("suggest/{term}")
    suspend fun suggest(@Path("term") term: String): retrofit2.Response<DeezerSuggestResponse>
}

object LyricsOvhApi {
    val service: LyricsOvhApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.lyrics.ovh/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsOvhApiService::class.java)
    }
}
