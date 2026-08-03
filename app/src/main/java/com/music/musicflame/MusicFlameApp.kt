package com.music.musicflame

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MusicFlameApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase debe estar inicializado antes de instalar App Check
        FirebaseApp.initializeApp(this)

        val appCheck = FirebaseAppCheck.getInstance()

        // Usamos el Debug Provider en TODOS los builds (debug y release).
        // Esto es porque distribuimos el APK directo (GitHub), no por Play Store,
        // así que Play Integrity no puede funcionar (necesita el paquete
        // registrado en Google Play Console). El secreto de este provider
        // ya está registrado en Firebase Console -> App Check -> Manage debug tokens.
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("MusicFlameApp", "App Check: usando DebugAppCheckProviderFactory")
    }
}