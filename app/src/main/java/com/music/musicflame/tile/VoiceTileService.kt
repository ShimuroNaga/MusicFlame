package com.music.musicflame.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.music.musicflame.R
import com.music.musicflame.voice.VoiceAssistantActivity
import com.music.musicflame.voice.VoiceSettings

/**
 * Tile de acceso rápido: Asistente de voz. Abre la pantalla de escucha (si el asistente está activado en
 * Ajustes > IA > Asistente de voz; si no, esa pantalla avisa y se cierra).
 */
class VoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, VoiceAssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (VoiceSettings(this).isEnabled()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_voice)
        tile.label = getString(R.string.tile_voice)
        tile.updateTile()
    }
}
