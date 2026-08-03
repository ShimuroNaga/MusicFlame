package com.music.musicflame.data

// Se movió aquí porque antes vivía dentro de GeminiRepository.kt (que usaba
// Firebase AI Logic y ya se quitó). Se deja este tipo solo para no romper el
// estado que ya existía en MainActivity/GeminiScreen mientras esa pantalla
// está vacía/en pausa.
data class AlbumRepository(val role: String, val text: String)
