package com.music.musicflame.together

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Icono de "En compañía" del usuario (como el avatar de Discord).
 *
 * Se guarda en local como un JPEG pequeño en base64 (SharedPreferences "together_prefs") y se
 * publica en Firebase en rooms/{código}/members/{uid}/avatar para que los demás lo vean.
 * Con 160 px y calidad 82 pesa ~6–10 KB: cabe de sobra en Realtime Database y no hace falta
 * Firebase Storage ni cambiar las reglas (cada quien ya puede escribir su propio nodo).
 */
object TogetherAvatar {
    const val SIZE_PX = 160
    private const val PREFS = "together_prefs"
    private const val KEY_AVATAR = "avatar_b64"
    /** Tope de seguridad al leer el icono de otro usuario (base64). */
    private const val MAX_B64_CHARS = 60_000

    fun load(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AVATAR, null)

    /** Guarda [bitmap] (ya recortado, cuadrado) y avisa a la sala si hay una abierta. */
    fun save(context: Context, bitmap: Bitmap) {
        val out = Bitmap.createScaledBitmap(bitmap, SIZE_PX, SIZE_PX, true)
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 82, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR, b64).apply()
        TogetherManager.current?.refreshAvatar()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_AVATAR).apply()
        TogetherManager.current?.refreshAvatar()
    }

    /** Decodifica el base64 (propio o de otro miembro). Devuelve null si es inválido o enorme. */
    fun decode(b64: String?): Bitmap? {
        if (b64.isNullOrEmpty() || b64.length > MAX_B64_CHARS) return null
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) { null }
    }
}
