package com.music.musicflame

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine

class RemoteConfigManager {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        // Configuramos Firebase para que actualice rápido mientras desarrollamos
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0) // En producción (Play Store) se recomienda 3600
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Valores por defecto (si el usuario no tiene internet la primera vez)
        // IMPORTANTE: "gemini-1.5-flash" y "gemini-2.0-flash" ya fueron retirados por Google
        // (devuelven 404). Usamos modelos vigentes como fallback.
        val defaults = mapOf(
            "gemini_model_name" to "gemini-2.5-flash"
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    /**
     * Descarga la config más reciente desde Firebase y la activa.
     * Llama esto una vez al iniciar la app (ej. en el Application class o en el splash/MainActivity),
     * antes de crear el GeminiRepository.
     */
    suspend fun fetchAndActivate(): Boolean = suspendCancellableCoroutine { cont ->
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("RemoteConfigManager", "Config actualizada. Modelo actual: ${getModelName()}")
                } else {
                    Log.w("RemoteConfigManager", "No se pudo actualizar Remote Config, se usan los valores por defecto/caché", task.exception)
                }
                if (cont.isActive) cont.resumeWith(Result.success(task.isSuccessful))
            }
    }

    /**
     * Devuelve el nombre del modelo Gemini a usar, leído de Remote Config.
     * Si el parámetro no existe o falló el fetch, devuelve el valor por defecto/caché.
     */
    fun getModelName(): String {
        return remoteConfig.getString("gemini_model_name")
    }
}