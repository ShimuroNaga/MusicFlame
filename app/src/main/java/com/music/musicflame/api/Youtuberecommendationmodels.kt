package com.music.musicflame.api

// --- Respuesta de subscriptions.list ---
data class SubscriptionListResponse(
    val items: List<SubscriptionItem> = emptyList()
)

data class SubscriptionItem(
    val snippet: SubscriptionSnippet? = null
)

data class SubscriptionSnippet(
    val title: String? = null,
    val resourceId: YtResourceId? = null
)

// --- Respuesta de channels.list (part=contentDetails) ---
data class ChannelListResponse(
    val items: List<ChannelItem> = emptyList()
)

data class ChannelItem(
    val id: String? = null,
    val contentDetails: ChannelContentDetails? = null
)

data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists? = null
)

data class RelatedPlaylists(
    val uploads: String? = null
)

// --- Respuesta de playlistItems.list ---
data class PlaylistItemsResponse(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val snippet: PlaylistItemSnippet? = null
)

data class PlaylistItemSnippet(
    val title: String? = null,
    val channelTitle: String? = null,
    val resourceId: YtResourceId? = null,
    val thumbnails: YtThumbnails? = null
)

// --- Compartidas ---
// El campo "resourceId" trae channelId (en subscriptions) o videoId (en playlistItems),
// según el endpoint. Gson simplemente deja en null el que no venga en la respuesta.
data class YtResourceId(
    val channelId: String? = null,
    val videoId: String? = null
)

data class YtThumbnails(
    val high: YtThumbnailInfo? = null,
    val default: YtThumbnailInfo? = null
)

data class YtThumbnailInfo(
    val url: String? = null
)