package com.music.musicflame.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.music.musicflame.data.SettingsRepository

@Composable
fun MusicFlameTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }

    // Estados reactivos que almacenan las preferencias actuales del usuario
    val appThemeState = remember { mutableStateOf(settingsRepo.getAppTheme()) }
    val isAmoledState = remember { mutableStateOf(settingsRepo.isAmoledModeEnabled()) }

    // Registrar un listener para detectar cambios en SharedPreferences en tiempo real
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "app_theme" -> appThemeState.value = settingsRepo.getAppTheme()
                // Nota: Asegúrate de que "amoled_mode" sea exactamente el nombre
                // de la clave que usas en tu SettingsRepository para guardar esta opción.
                "amoled_mode" -> isAmoledState.value = settingsRepo.isAmoledModeEnabled()
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Determinar si debemos aplicar el modo oscuro según la opción seleccionada
    val isDarkTheme = when (appThemeState.value) {
        "Fondo oscuro" -> true
        "Fondo blanco" -> false
        else -> isSystemInDarkTheme() // "Siguiendo al sistema"
    }

    // Configuración del esquema de colores base (Soporta colores dinámicos Material You desde Android 12)
    val baseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        if (isDarkTheme) darkColorScheme()
        else lightColorScheme()
    }

    // Si es modo oscuro Y el modo AMOLED está activo, forzamos los fondos a negro puro
    val finalColorScheme = if (isDarkTheme && isAmoledState.value) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        content = content
    )
}