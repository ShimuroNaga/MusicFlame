package com.music.musicflame.data

/**
 * Modelos de la respuesta de POST https://api.lemonsqueezy.com/v1/licenses/validate
 *
 * Los nombres de los campos siguen tal cual el JSON que devuelve Lemon Squeezy
 * (snake_case), igual que ya se hace en este proyecto con otras APIs externas
 * (ver DeezerTrack.title_short en LyricsOvhApi.kt), para no depender de
 * anotaciones extra de Gson.
 *
 * Documentación oficial: https://docs.lemonsqueezy.com/help/licensing/license-api
 */

data class LemonSqueezyLicenseKey(
    val id: Long? = null,
    // "active", "inactive", "expired" o "disabled"
    val status: String? = null,
    val key: String? = null,
    val activation_limit: Int? = null,
    val activation_usage: Int? = null,
    val created_at: String? = null,
    val expires_at: String? = null
)

data class LemonSqueezyMeta(
    val store_id: Long? = null,
    val order_id: Long? = null,
    val product_id: Long? = null,
    val product_name: String? = null,
    val variant_name: String? = null,
    val customer_id: Long? = null,
    val customer_name: String? = null,
    val customer_email: String? = null
)

/**
 * `valid` es el campo autoritativo: true solo si la key existe, no fue
 * revocada/reembolsada y no está expirada. `license_key.status` se usa
 * únicamente para dar un mensaje de error más específico cuando `valid`
 * viene en false.
 */
data class LemonSqueezyValidateResponse(
    val valid: Boolean = false,
    val error: String? = null,
    val license_key: LemonSqueezyLicenseKey? = null,
    val meta: LemonSqueezyMeta? = null
)
