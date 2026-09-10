package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.music.musicflame.R

object AppIconManager {

    private const val PACKAGE = "com.music.musicflame"

    private val aliases = mapOf(
        "default" to "$PACKAGE.IconDefault",
        "classic" to "$PACKAGE.IconClassic",
        "brilliant" to "$PACKAGE.IconBrilliant",
        "pixel" to "$PACKAGE.IconPixel",
        "cookies" to "$PACKAGE.IconCookies",
        "gray" to "$PACKAGE.IconGray",
        "remix" to "$PACKAGE.IconRemixFlame",
        "demonmusic" to "$PACKAGE.IconDemonMusic",
        "musicpika" to "$PACKAGE.IconMusicPika",
        "shimuflame" to "$PACKAGE.IconShimuFlame"
    )

    // Mismo mapeo clave->recurso que appIconOptions en SettingsScreen.kt (ahí
    // se usa para el preview del selector, como Triple(key, label, previewRes)
    // atado a Compose). Se repite aquí porque este objeto no depende de
    // Compose y necesita el mismo dato desde otro lado (SongArtBitmapLoader).
    // Si agregas un ícono nuevo, actualiza los dos lados.
    private val iconDrawables = mapOf(
        "default" to R.mipmap.ic_launcher_musicflameupgrade,
        "classic" to R.mipmap.ic_launcher,
        "brilliant" to R.mipmap.ic_launcher_brilliant,
        "pixel" to R.mipmap.ic_launcher_pixel,
        "cookies" to R.mipmap.ic_launcher_cookies,
        "gray" to R.mipmap.ic_launcher_gray,
        "remix" to R.mipmap.ic_launcher_remixflame,
        "demonmusic" to R.mipmap.ic_launcher_demonmusic,
        "musicpika" to R.mipmap.ic_launcher_musicpika,
        "shimuflame" to R.mipmap.ic_launcher_shimuflame
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

    /**
     * Devuelve el recurso mipmap del icono que el usuario tiene elegido ahora
     * mismo en Ajustes > Icono de la app. Se usa como respaldo final de
     * carátula en la notificación de reproducción (ver SongArtBitmapLoader)
     * cuando la canción actual no trae ninguna carátula real: el respaldo
     * nativo del sistema usa el ícono "de fábrica" del manifest, que no
     * refleja el selector, así que este método es lo que sí lo hace.
     */
    fun getSelectedIconDrawableRes(context: Context): Int {
        val key = SettingsRepository(context).getSelectedAppIcon()
        return iconDrawables[key] ?: R.mipmap.ic_launcher_musicflameupgrade
    }
}