package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.music.musicflame.R

object AppIconManager {

    private const val PACKAGE = "com.music.musicflame"
    private const val MIGRATION_KEY = "removed_icons_v1"

    private val aliases = mapOf(
        "default" to "$PACKAGE.IconDefault",
        "classic" to "$PACKAGE.IconClassic",
        "cookies" to "$PACKAGE.IconCookies",
        "remix" to "$PACKAGE.IconRemixFlame",
        "musicpika" to "$PACKAGE.IconMusicPika",
        "musicbear" to "$PACKAGE.IconMusicBear"
    )

    // Mismo mapeo clave->recurso que appIconOptions en SettingsScreen.kt (ahí
    // se usa para el preview del selector, como Triple(key, label, previewRes)
    // atado a Compose). Se repite aquí porque este objeto no depende de
    // Compose y necesita el mismo dato desde otro lado (SongArtBitmapLoader).
    // Si agregas un ícono nuevo, actualiza los dos lados.
    private val iconDrawables = mapOf(
        "default" to R.mipmap.ic_launcher_musicflameupgrade,
        "classic" to R.mipmap.ic_launcher,
        "cookies" to R.mipmap.ic_launcher_cookies,
        "remix" to R.mipmap.ic_launcher_remixflame,
        "musicpika" to R.mipmap.ic_launcher_musicpika,
        "musicbear" to R.mipmap.ic_launcher_musicbear
    )

    /**
     * Migración: si el usuario tenía guardado un icono que ya se eliminó
     * (Brillante, Pixelart, Escala de grises, DemonMusic o ShimuFlame), su alias
     * desapareció del manifest y la app podría quedarse sin icono en el launcher.
     * Se llama al arrancar (MusicFlameApp), en un hilo de fondo, y corre UNA sola vez: al terminar deja
     * una marca. Si un día se elimina otro icono, sube [MIGRATION_KEY] (v2, v3…) para que vuelva a correr.
     * Lo devuelve al icono por defecto.
     */
    fun migrateRemovedIcon(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("app_icon_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_KEY, false)) return
        val repo = SettingsRepository(app)
        val saved = repo.getSelectedAppIcon()
        if (!aliases.containsKey(saved)) {
            repo.saveSelectedAppIcon("default")
            setIcon(app, "default")
        }
        prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
    }

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