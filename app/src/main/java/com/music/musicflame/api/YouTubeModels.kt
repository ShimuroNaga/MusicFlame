package com.music.musicflame.api

data class YouTubeResponse(val items: List<VideoItem>)
data class VideoItem(val id: VideoId, val snippet: VideoSnippet)
data class VideoId(val videoId: String)
data class VideoSnippet(val title: String, val channelTitle: String, val thumbnails: Thumbnails)
// Cambia esto en YouTubeModels.kt para probar:
data class Thumbnails(val high: ThumbnailUrl?) // Agrega el signo de interrogación ?
data class ThumbnailUrl(val url: String?)       // Agrega el signo de interrogación ?
