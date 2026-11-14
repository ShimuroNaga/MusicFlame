package com.music.musicflame.data

import com.music.musicflame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Consulta a Supabase que productos de la tienda de Ko-fi compro una cuenta de Google.
 *
 * Ko-fi no tiene API para consultar compras: solo avisa por webhook (Edge Function "kofi-webhook"
 * -> tabla kofi_purchases). La app llama a la Edge Function "kofi-restore" con un ID token de
 * Google; el servidor lo verifica y devuelve solo los codigos de ESE correo. No hay secretos
 * en el APK y no se puede consultar el correo de otra persona.
 *
 * Usa SUPABASE_URL / SUPABASE_KEY de BuildConfig, igual que SupabaseSalas.
 */
object KofiPurchasesApi {

    sealed class Result {
        data class Ok(val codes: Set<String>) : Result()
        object Failure : Result()
    }

    suspend fun fetchPurchasedCodes(idToken: String): Result = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_KEY
        if (base.isBlank() || key.isBlank() || idToken.isBlank()) return@withContext Result.Failure

        try {
            val conn = URL("$base/functions/v1/kofi-restore").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.doOutput = true
                conn.setRequestProperty("apikey", key)
                if (key.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("id_token", idToken).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return@withContext Result.Failure
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).getJSONArray("codes")
                val codes = mutableSetOf<String>()
                for (i in 0 until arr.length()) codes.add(arr.getString(i))
                Result.Ok(codes)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.Failure
        }
    }
}
