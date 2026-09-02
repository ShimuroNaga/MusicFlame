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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val appTextColorState = remember { mutableStateOf(settingsRepo.getAppTextColor()) }
    val customTextColorHexState = remember { mutableStateOf(settingsRepo.getCustomTextColorHex()) }
    // Color único del "Now Playing" indicator (catálogo, punto 3). Mismo
    // patrón "Adaptativo"/"Personalizado" que el color del ecualizador.
    val nowPlayingColorModeState = remember { mutableStateOf(settingsRepo.getNowPlayingColorMode()) }
    val nowPlayingCustomColorHexState = remember { mutableStateOf(settingsRepo.getNowPlayingCustomColorHex()) }
    // Tipo de letra global (ver AppFonts.kt, SettingsRepository.getAppFont).
    // Se guarda como AppFont.id (String); se resuelve a AppFont más abajo.
    val appFontIdState = remember { mutableStateOf(settingsRepo.getAppFont()) }
    // Blinda el RENDERIZADO global (texto de la app y "Now Playing"): si el
    // valor guardado es una opción de pago y el usuario no está desbloqueado,
    // se ignora acá abajo y se cae al comportamiento gratis de siempre, en
    // vez de seguir mostrando la personalización sin haber pagado.
    //
    // Reactivo (ver ProStatusHolder): antes esto quedaba fijo con
    // remember{} desde que arrancaba la app, así que iniciar sesión con la
    // cuenta dueña o activar una license key no se reflejaba en el Arcoíris
    // hasta cerrar y reabrir la app por completo.
    val isProUnlocked = com.music.musicflame.data.ProStatusHolder.isProUnlocked

    // Primer chequeo al entrar en composición (por si esta instancia de
    // MusicFlameTheme se crea antes de que MainActivity haya corrido su
    // propio refresh de arranque).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.musicflame.data.ProStatusHolder.refresh(context)
    }

    // Registrar listeners para detectar cambios en SharedPreferences en tiempo real
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "app_theme" -> appThemeState.value = settingsRepo.getAppTheme()
                // Nota: Asegúrate de que "amoled_mode" sea exactamente el nombre
                // de la clave que usas en tu SettingsRepository para guardar esta opción.
                "amoled_mode" -> isAmoledState.value = settingsRepo.isAmoledModeEnabled()
                "app_text_color" -> appTextColorState.value = settingsRepo.getAppTextColor()
                "custom_text_color_hex" -> customTextColorHexState.value = settingsRepo.getCustomTextColorHex()
                "now_playing_color_mode" -> nowPlayingColorModeState.value = settingsRepo.getNowPlayingColorMode()
                "now_playing_custom_color_hex" -> nowPlayingCustomColorHexState.value = settingsRepo.getNowPlayingCustomColorHex()
                "app_font" -> appFontIdState.value = settingsRepo.getAppFont()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // NUEVO: mismo patrón pero para el archivo de preferencias de la
        // licencia (pago). Así, si se activa/revoca/quita la license key
        // desde Ajustes, el tema global se entera al instante.
        val licensePrefs = context.getSharedPreferences("license", Context.MODE_PRIVATE)
        val licenseListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "license_status" || key == "license_key") {
                com.music.musicflame.data.ProStatusHolder.refresh(context)
            }
        }
        licensePrefs.registerOnSharedPreferenceChangeListener(licenseListener)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            licensePrefs.unregisterOnSharedPreferenceChangeListener(licenseListener)
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

    val isDarkBackground = finalColorScheme.background.luminance() < 0.5f

    // Fase animada del modo "Arcoíris" (catálogo, punto 6). Se calcula una
    // sola vez acá arriba y se reusa en texto y Now Playing para que ambos
    // (si los dos están en Arcoíris) queden sincronizados entre sí.
    val rainbowPhase = rememberRainbowPhase()

    val appTextColor = when {
        !isProUnlocked && appTextColorState.value == COLOR_MODE_RAINBOW ->
            if (isDarkBackground) Color.White else Color.Black // Arcoíris es de pago: cae al default
        appTextColorState.value == "Personalizado" -> parseCustomTextColor(customTextColorHexState.value)
        appTextColorState.value == COLOR_MODE_RAINBOW -> rainbowColorAt(rainbowPhase.value)
        else -> if (isDarkBackground) Color.White else Color.Black // "Negro", "Blanco" o cualquier default
    }
    val nowPlayingIndicatorColor = when {
        !isProUnlocked && (nowPlayingColorModeState.value == "Personalizado" || nowPlayingColorModeState.value == COLOR_MODE_RAINBOW) ->
            if (isDarkBackground) Color.White else Color.Black // Personalizado/Arcoíris son de pago: cae al default
        nowPlayingColorModeState.value == "Personalizado" -> parseCustomTextColor(nowPlayingCustomColorHexState.value)
        nowPlayingColorModeState.value == COLOR_MODE_RAINBOW -> rainbowColorAt(rainbowPhase.value)
        else -> if (isDarkBackground) Color.White else Color.Black // "Adaptativo": mismo default histórico del componente
    }

    // Blinda el tipo de letra igual que el color de texto/Now Playing: si lo
    // guardado es una fuente de pago y el usuario no está desbloqueado, cae
    // a Roboto en vez de seguir mostrando la fuente premium sin haber pagado.
    val storedFont = AppFont.fromId(appFontIdState.value)
    val effectiveFont = if (!isProUnlocked && !storedFont.isFree) AppFont.DEFAULT else storedFont
    val appTypography = appTypographyFor(effectiveFont.fontFamily)

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = appTypography
    ) {
        CompositionLocalProvider(
            LocalAppTextColor provides appTextColor,
            LocalNowPlayingIndicatorColor provides nowPlayingIndicatorColor
        ) {
            content()
        }
    }
}

/**
 * Calcula si el fondo REAL que va a usar la app en este momento es oscuro o claro,
 * con la MISMA lógica que usa MusicFlameTheme (arriba) para elegir texto blanco/negro
 * automáticamente: tema elegido, colores dinámicos Material You, y modo AMOLED.
 *
 * Por qué existe: los presets "Negro"/"Blanco" ya NO determinan el color real (ver
 * appTextColor arriba: siempre se auto-corrigen contra la luminancia del fondo). Pero
 * pantallas como el wizard de bienvenida o Ajustes mostraban el string CRUDO guardado
 * ("Negro" por defecto de fábrica) como si fuera el que se está aplicando de verdad,
 * aunque el texto que realmente se ve en pantalla fuera blanco (por la
 * auto-corrección). Con esta función, esas pantallas pueden preguntar "¿qué color se
 * está usando DE VERDAD ahora mismo?" en vez de confiar en el string guardado.
 */
@Composable
fun resolveIsDarkBackground(settingsRepo: SettingsRepository): Boolean {
    val context = LocalContext.current
    val isDarkTheme = when (settingsRepo.getAppTheme()) {
        "Fondo oscuro" -> true
        "Fondo blanco" -> false
        else -> isSystemInDarkTheme()
    }
    val baseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDarkTheme) darkColorScheme() else lightColorScheme()
    }
    val finalBackground = if (isDarkTheme && settingsRepo.isAmoledModeEnabled()) {
        Color.Black
    } else {
        baseColorScheme.background
    }
    return finalBackground.luminance() < 0.5f
}