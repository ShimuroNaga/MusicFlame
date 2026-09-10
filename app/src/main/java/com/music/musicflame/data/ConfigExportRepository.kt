package com.music.musicflame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exporta/importa la configuración de MusicFlame (Ajustes) como un archivo
 * .json portátil, para copiarla a otro dispositivo o guardarla como respaldo.
 *
 * A propósito NO incluye:
 * - Cualquier Uri "content://" (imagen de fondo, GIF del reproductor,
 *   carátulas personalizadas de playlists/Lo Más Sonado/Por Descubrir, ver
 *   AppIconManager/PlaylistsScreen): esas Uris apuntan a archivos de ESTE
 *   dispositivo, en otro celular no resuelven a nada. El usuario las vuelve
 *   a elegir a mano si quiere, después de importar.
 * - Favoritos, Mezcla del día, fecha de onboarding: son datos/estado de la
 *   biblioteca (IDs de MediaStore de ESTE dispositivo), no "configuración".
 *
 * Todo lo demás en el archivo de prefs "settings" (tema, colores, tipografía,
 * ecualizador, forma de carátula, ícono elegido, filtros de duración,
 * formatos ocultos, etc.) sí es portátil y se exporta completo leyendo
 * prefs.all directamente, sin tener que mantener una lista de claves aparte
 * cada vez que se agrega un ajuste nuevo a SettingsRepository.
 */
object ConfigExportRepository {

    private const val SCHEMA_VERSION = 1

    // Claves de "settings" que NUNCA se exportan ni se importan (ver razones
    // en el comentario de arriba). Si agregas una key nueva en
    // SettingsRepository que guarde una Uri de contenido o datos de
    // biblioteca/estado (no una preferencia real), agrégala aquí también.
    private val EXCLUDED_KEYS = setOf(
        "background_image_uri",
        "player_gif_uri",
        "favorite_songs_set",
        "favorites_cover_uri",
        "most_played_cover_uri",
        "never_played_cover_uri",
        "mix_songs",
        "last_mix_date",
        "onboarding_completed"
    )

    fun exportToJson(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val entries = JSONObject()

        prefs.all.forEach { (key, value) ->
            if (key in EXCLUDED_KEYS || value == null) return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> { entry.put("t", "b"); entry.put("v", value) }
                is Int -> { entry.put("t", "i"); entry.put("v", value) }
                is Float -> { entry.put("t", "f"); entry.put("v", value.toDouble()) }
                is Long -> { entry.put("t", "l"); entry.put("v", value) }
                is String -> { entry.put("t", "s"); entry.put("v", value) }
                is Set<*> -> {
                    entry.put("t", "ss")
                    val arr = JSONArray()
                    value.forEach { arr.put(it.toString()) }
                    entry.put("v", arr)
                }
                else -> return@forEach
            }
            entries.put(key, entry)
        }

        val root = JSONObject()
        root.put("app", "musicflame")
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("settings", entries)
        return root.toString(2)
    }

    /**
     * Aplica un JSON generado por [exportToJson]. Devuelve la cantidad de
     * ajustes aplicados, o lanza IllegalArgumentException (con un mensaje
     * pensado para mostrarse tal cual en un Toast) si el archivo no tiene el
     * formato esperado.
     */
    fun importFromJson(context: Context, json: String): Int {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("El archivo no es un JSON válido")
        }

        if (root.optString("app") != "musicflame") {
            throw IllegalArgumentException("Este archivo no es una configuración de MusicFlame")
        }

        val entries = root.optJSONObject("settings")
            ?: throw IllegalArgumentException("El archivo no tiene ajustes para importar")

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var applied = 0

        entries.keys().forEach { key ->
            if (key in EXCLUDED_KEYS) return@forEach
            val entry = entries.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "l" -> editor.putLong(key, entry.optLong("v"))
                "s" -> editor.putString(key, entry.optString("v"))
                "ss" -> {
                    val arr = entry.optJSONArray("v") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet(key, set)
                }
                else -> return@forEach
            }
            applied++
        }

        editor.apply()
        return applied
    }
}
