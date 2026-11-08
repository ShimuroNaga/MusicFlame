package com.music.musicflame.data

import com.music.musicflame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Consulta a Supabase qué productos de la tienda de Ko-fi compró un correo.
 *
 * Ko-fi no tiene API para consultar compras: solo avisa por webhook. Ese aviso lo
 * recibe la Edge Function "kofi-webhook" (ver supabase/functions) y lo guarda en la
 * tabla kofi_purchases. La app llama a la función SQL get_kofi_purchases(p_email, p_token),
 * que valida un HMAC-SHA256 del correo antes de devolver los códigos.
 *
 * Fix anti-IDOR: sin el token correcto, nadie puede consultar el correo de otra persona
 * aunque tenga la clave publishable. El secreto vive en local.properties (KOFI_HMAC_SECRET)
 * y en el servidor como custom GUC (app.kofi_hmac_secret) o Supabase Vault.
 *
 * Usa SUPABASE_URL / SUPABASE_KEY de BuildConfig, igual que SupabaseSalas.
 */
object KofiPurchasesApi {

    sealed class Result {
        data class Ok(val codes: Set<String>) : Result()
        object Failure : Result()
    }

    /**
     * Calcula HMAC-SHA256(correo_normalizado, secreto_hex) y devuelve el resultado en hex.
     * Mismo algoritmo que usa la función SQL en Supabase.
     */
    private fun hmacToken(email: String): String {
        val secret = BuildConfig.KOFI_HMAC_SECRET
        if (secret.isBlank()) return ""
        return try {
            val keyBytes = secret.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            val raw = mac.doFinal(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            raw.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun fetchPurchasedCodes(email: String): Result = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_KEY
        if (base.isBlank() || key.isBlank()) return@withContext Result.Failure

        val token = hmacToken(email)
        if (token.isBlank()) return@withContext Result.Failure  // secreto no configurado

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
                val payload = JSONObject()
                    .put("p_email", email.trim().lowercase())
                    .put("p_token", token)
                    .toString()
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
