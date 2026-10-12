package com.music.musicflame.data

import com.music.musicflame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Consulta a Supabase qué productos de la tienda de Ko-fi compró un correo.
 *
 * Ko-fi no tiene API para consultar compras: solo avisa por webhook. Ese aviso lo
 * recibe la Edge Function "kofi-webhook" (ver supabase/functions) y lo guarda en la
 * tabla kofi_purchases. La app solo puede llamar a la función SQL get_kofi_purchases(),
 * que devuelve los códigos de producto (direct_link_code) del correo dado.
 *
 * Usa SUPABASE_URL / SUPABASE_KEY de BuildConfig, igual que SupabaseSalas.
 */
object KofiPurchasesApi {

    sealed class Result {
        data class Ok(val codes: Set<String>) : Result()
        object Failure : Result()
    }

    suspend fun fetchPurchasedCodes(email: String): Result = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_KEY
        if (base.isBlank() || key.isBlank()) return@withContext Result.Failure
        try {
            val conn = URL("$base/rest/v1/rpc/get_kofi_purchases").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.doOutput = true
                conn.setRequestProperty("apikey", key)
                if (key.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("p_email", email).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return@withContext Result.Failure
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
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
