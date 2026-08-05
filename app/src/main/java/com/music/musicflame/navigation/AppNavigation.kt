package com.music.musicflame.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Settings

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Songs     : Screen("songs",     "Canciones", Icons.Filled.Home)
    object Playlists : Screen("playlists", "Playlists", Icons.Filled.List)
    object Album     : Screen("album",     "Albums",    Icons.Filled.Album)
    object Mix       : Screen("mix",       "Tu Mix",    Icons.Filled.Favorite)
    object Trash     : Screen("trash",     "Papelera",  Icons.Filled.Delete)

    object Settings : Screen("settings", "Ajustes", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Songs,
    Screen.Playlists,
    Screen.Album,
    Screen.Mix,
    Screen.Trash
)