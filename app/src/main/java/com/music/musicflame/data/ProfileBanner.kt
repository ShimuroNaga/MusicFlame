package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Banner horizontal del perfil (3:1 por defecto; el usuario puede deformar el recorte entre 1.5:1 y 4:1) de Cuenta, guardado en local como JPEG.
 * Se guarda en un archivo (no en SharedPreferences) porque pesa más que el avatar.
 */
object ProfileBanner {
    const val ASPECT = 3f
    const val MIN_ASPECT = 1.5f
    const val MAX_ASPECT = 4f
    const val WIDTH_PX = 960
    private const val FILE_NAME = "profile_banner.jpg"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(context: Context): Bitmap? = runCatching {
        val f = file(context)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    fun save(context: Context, bitmap: Bitmap) {
        runCatching {
            file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
