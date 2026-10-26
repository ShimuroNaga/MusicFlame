package com.music.musicflame

import android.app.Application
import com.music.musicflame.data.ProStatusHolder
import com.music.musicflame.data.SettingsStore

/**
 * Application de MusicFlame.
 *
 * Su única misión por ahora: arrancar la carga de los ajustes (DataStore) en segundo plano lo antes
 * posible. Application.onCreate corre antes que cualquier Activity, Service, widget, tile o receiver,
 * así que cuando alguno de ellos lee un ajuste de forma síncrona, normalmente la carga ya terminó
 * (o está a punto) y no hay "parpadeo" de tema ni lecturas de disco en el hilo principal.
 */
class MusicFlameApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.preload(this)
        ProStatusHolder.preload(this)
    }
}
