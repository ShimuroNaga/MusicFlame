package com.music.musicflame.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Instancia de DataStore exclusiva para el control de versiones y actualizaciones
private val Context.updateDataStore by preferencesDataStore(name = "musicflame_updates")

class UpdatePreferences(private val context: Context) {

    companion object {
        // Clave única para almacenar el tag de la versión ignorada (ej. "v1.2.0")
        private val IGNORED_VERSION_KEY = stringPreferencesKey("ignored_version")
        // Última release conocida y cuándo se consultó (el chequeo automático va como máximo cada 12 h)
        private val LAST_CHECK_KEY = longPreferencesKey("last_check_at")
        private val LATEST_TAG_KEY = stringPreferencesKey("latest_tag")
        private val LATEST_URL_KEY = stringPreferencesKey("latest_url")
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

    class KnownRelease(val checkedAt: Long, val tag: String, val url: String)

    suspend fun getKnownRelease(): KnownRelease {
        val p = context.updateDataStore.data.first()
        return KnownRelease(p[LAST_CHECK_KEY] ?: 0L, p[LATEST_TAG_KEY] ?: "", p[LATEST_URL_KEY] ?: "")
    }

    suspend fun saveKnownRelease(checkedAt: Long, tag: String, url: String) {
        context.updateDataStore.edit { p ->
            p[LAST_CHECK_KEY] = checkedAt
            p[LATEST_TAG_KEY] = tag
            p[LATEST_URL_KEY] = url
        }
    }
}
