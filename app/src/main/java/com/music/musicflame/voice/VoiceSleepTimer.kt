package com.music.musicflame.voice

import android.content.Context
import com.music.musicflame.data.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Temporizador de sueño del asistente. El de [MusicPlayerManager] vive dentro de cada instancia y el
 * asistente destruye la suya al cerrarse, así que aquí el temporizador pertenece al proceso: al cumplirse
 * pausa la música con un reproductor nuevo. (No aparece en el indicador del temporizador de la app.)
 */
object VoiceSleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun start(ctx: Context, minutes: Int) {
        cancel()
        val app = ctx.applicationContext
        job = scope.launch {
            delay(minutes.coerceIn(1, 600) * 60_000L)
            val pm = MusicPlayerManager(app)
            pm.whenReady { pm.pause(); pm.release() }
        }
    }

    fun cancel() { job?.cancel(); job = null }
}
