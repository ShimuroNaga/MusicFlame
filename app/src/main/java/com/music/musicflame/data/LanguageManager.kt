package com.music.musicflame.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Idiomas que ofrece la app. [tag] es el código BCP-47 (null = seguir el
 * idioma del sistema).
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}

/**
 * Cambio de idioma por app, sin dependencias extra.
 *
 * - Android 13+ (API 33): usa la API oficial LocaleManager. El sistema guarda
 *   la preferencia solo, la sincroniza con Ajustes del sistema > Idioma de la
 *   app y recrea la Activity por su cuenta.
 * - Android 12 / 12L (API 31-32, minSdk del proyecto): guarda el idioma en
 *   SharedPreferences y lo aplica en MainActivity.attachBaseContext() vía [wrap].
 *
 * Los textos de la UI viven en res/values/strings.xml (español, idioma base) y
 * res/values-en/strings.xml (inglés); Android elige la carpeta según el
 * idioma que fije este objeto.
 */
object LanguageManager {
    private const val PREFS = "musicflame_language"
    private const val KEY_TAG = "language_tag"

    /** Idioma elegido por el usuario hoy ([AppLanguage.SYSTEM] si no eligió ninguno). */
    fun current(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty()) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].language)
        }
        return AppLanguage.fromTag(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
        )
    }

    /** Aplica el idioma y recrea la pantalla para que se vea al instante. */
    fun apply(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            val list = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            // El sistema recrea la Activity automáticamente al cambiar el locale.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = list
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TAG, language.tag).apply()
            activity.recreate()
        }
    }

    /**
     * Para llamar desde attachBaseContext() de la Activity. En Android 13+ no
     * hace nada (el sistema ya aplica el locale); en 12/12L aplica el guardado.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
            ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Sube por los ContextWrapper hasta encontrar la Activity (LocalContext no siempre lo es directo). */
    fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
