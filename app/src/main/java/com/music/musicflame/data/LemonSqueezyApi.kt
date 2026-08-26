package com.music.musicflame.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Endpoint público de validación de licencias de Lemon Squeezy. No requiere
 * API key propia: cualquiera puede validar una license key contra este
 * endpoint (es el mismo diseño que usan lrclib.net/lyrics.ovh en este
 * proyecto: sin autenticación, solo un POST/GET directo).
 *
 * Docs: https://docs.lemonsqueezy.com/help/licensing/license-api#validating-a-license-key
 */
interface LemonSqueezyApiService {
    @FormUrlEncoded
    @POST("v1/licenses/validate")
    suspend fun validateLicense(
        @Field("license_key") licenseKey: String
    ): Response<LemonSqueezyValidateResponse>
}

object LemonSqueezyApi {
    val service: LemonSqueezyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.lemonsqueezy.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LemonSqueezyApiService::class.java)
    }
}
