package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Banner horizontal del perfil de Cuenta: tamaño exacto WIDTH_PX x HEIGHT_PX (proporción fija 3:1), guardado en local como JPEG.
 * Se guarda en un archivo (no en SharedPreferences) porque pesa más que el avatar.
 */
object ProfileBanner {
    const val ASPECT = 3f
    const val WIDTH_PX = 960
    const val HEIGHT_PX = 320 // WIDTH_PX / ASPECT
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
