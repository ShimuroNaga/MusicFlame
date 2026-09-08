package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda en almacenamiento interno de la app (files/, no requiere permisos)
 * el Bitmap que devuelve ImageCropperDialog, y regresa una Uri de tipo file://
 * apuntando a ese archivo.
 *
 * Por qué file:// y no FileProvider/content://: esta Uri solo se usa DENTRO
 * de la propia app (se guarda como String en SharedPreferences y se vuelve a
 * abrir con contentResolver.openInputStream en AlbumArt/RealTagWriter/fondo de
 * pantalla), nunca se manda en un Intent hacia otra app, así que no aplica la
 * restricción de FileUriExposedException ni hace falta declarar un provider.
 *
 * Cada llamada usa un nombre de archivo único (timestamp) dentro de la
 * subcarpeta indicada, para no pisar un recorte anterior mientras el usuario
 * todavía tiene el diálogo de edición abierto (por ejemplo, si recorta,
 * cancela, y vuelve a recortar).
 */
object CroppedImageStorage {

    fun saveCroppedBitmap(context: Context, bitmap: Bitmap, subfolder: String): Uri {
        val dir = File(context.filesDir, subfolder).apply { mkdirs() }
        val file = File(dir, "crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return Uri.fromFile(file)
    }
}
