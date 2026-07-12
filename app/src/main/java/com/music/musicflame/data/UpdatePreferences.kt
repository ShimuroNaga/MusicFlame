package com.music.musicflame.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Instancia de DataStore exclusiva para el control de versiones y actualizaciones
private val Context.updateDataStore by preferencesDataStore(name = "musicflame_updates")

class UpdatePreferences(private val context: Context) {

    companion object {
        // Clave única para almacenar el tag de la versión ignorada (ej. "v1.2.0")
        private val IGNORED_VERSION_KEY = stringPreferencesKey("ignored_version")
    }

    /**
     * Devuelve un Flow con la versión que el usuario decidió ignorar.
     * Si el usuario nunca ha rechazado una actualización, emitirá 'null'.
     */
    val ignoredVersionFlow: Flow<String?> = context.updateDataStore.data.map { preferences ->
        preferences[IGNORED_VERSION_KEY]
    }

    /**
     * Guarda el tag de la versión que el usuario rechazó para no volver a mostrársela
     * automáticamente en el inicio.
     */
    suspend fun saveIgnoredVersion(versionTag: String) {
        context.updateDataStore.edit { preferences ->
            preferences[IGNORED_VERSION_KEY] = versionTag
        }
    }

    /**
     * Borra el registro de la versión ignorada.
     * Puede ser útil si en el futuro necesitas resetear este estado.
     */
    suspend fun clearIgnoredVersion() {
        context.updateDataStore.edit { preferences ->
            preferences.remove(IGNORED_VERSION_KEY)
        }
    }
}