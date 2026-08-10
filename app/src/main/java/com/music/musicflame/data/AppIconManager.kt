package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.music.musicflame.R

object AppIconManager {

    private const val PACKAGE = "com.music.musicflame"

    private val aliases = mapOf(
        "default" to "$PACKAGE.IconDefault",
        "brilliant" to "$PACKAGE.IconBrilliant",
        "pixel" to "$PACKAGE.IconPixel",
        "cookies" to "$PACKAGE.IconCookies",
        "gray" to "$PACKAGE.IconGray",
        "remix" to "$PACKAGE.IconRemixFlame"
    )

    // Tema de splash que corresponde a cada icono. Debe mantenerse en sync
    // con res/values/themes_splash.xml y con las keys de "aliases".
    private val splashThemes = mapOf(
        "default" to R.style.Theme_MusicFlame_Splash_Default,
        "brilliant" to R.style.Theme_MusicFlame_Splash_Brilliant,
        "pixel" to R.style.Theme_MusicFlame_Splash_Pixel,
        "cookies" to R.style.Theme_MusicFlame_Splash_Cookies,
        "gray" to R.style.Theme_MusicFlame_Splash_Gray,
        "remix" to R.style.Theme_MusicFlame_Splash_Remix
    )

    fun setIcon(context: Context, key: String) {
        val pm = context.packageManager
        aliases.forEach { (iconKey, aliasName) ->
            val state = if (iconKey == key)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(
                ComponentName(context, aliasName),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** Devuelve el resource id del tema de splash asociado a [key], con fallback al default. */
    fun splashThemeFor(key: String): Int =
        splashThemes[key] ?: R.style.Theme_MusicFlame_Splash_Default
}