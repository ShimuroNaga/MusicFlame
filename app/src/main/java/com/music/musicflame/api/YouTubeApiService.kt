package com.music.musicflame.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import com.music.musicflame.BuildConfig

interface YouTubeApiService {

    // 1. Búsqueda normal (el que ya tenías)
    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): YouTubeResponse

    // 2. Tendencias de música (Plan B: cuando NO hay cuenta)
    @GET("videos")
    suspend fun getPopularMusicVideos(
        @Query("part") part: String = "snippet",
        @Query("chart") chart: String = "mostPopular",
        @Query("videoCategoryId") videoCategoryId: String = "10",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): YouTubeResponse

    // 3. Videos que le gustan al usuario (Plan A: cuando SÍ hay cuenta)
    @GET("videos")
    suspend fun getLikedVideos(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet",
        @Query("myRating") myRating: String = "like",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): YouTubeResponse

    // --- NUEVO: para armar recomendados basados en canales suscritos ---

    // 4. Lista de canales a los que está suscrito el usuario (requiere OAuth)
    @GET("subscriptions")
    suspend fun getMySubscriptions(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 15,
        @Query("order") order: String = "alphabetical",
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): SubscriptionListResponse

    // 5. Info de canales (para sacar el ID de su playlist "Subidos") — hasta 50 IDs en un solo request
    @GET("channels")
    suspend fun getChannelsContentDetails(
        @Query("id") channelIds: String,
        @Query("part") part: String = "contentDetails",
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): ChannelListResponse

    // 6. Videos recientes de la playlist "Subidos" de un canal específico
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): PlaylistItemsResponse
}