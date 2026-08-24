package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

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
}