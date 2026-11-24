package com.music.musicflame.data

import com.music.musicflame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.music.musicflame.api.AppHttp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
            val payload = JSONObject().put("id_token", idToken).toString()
            val rb = Request.Builder()
                .url("$base/functions/v1/kofi-restore")
                .header("apikey", key)
                .post(payload.toRequestBody("application/json".toMediaType()))
            if (key.startsWith("eyJ")) rb.header("Authorization", "Bearer $key")
            AppHttp.client.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Failure
                val body = resp.body?.string() ?: return@withContext Result.Failure
                val arr = JSONObject(body).getJSONArray("codes")
                val codes = mutableSetOf<String>()
                for (i in 0 until arr.length()) codes.add(arr.getString(i))
                Result.Ok(codes)
            }
        } catch (e: Exception) {
            Result.Failure
        }
    }
}
