package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn

enum class LicenseStatus { INACTIVE, ACTIVE, ERROR }

/** Resultado de validar una key nueva que el usuario acaba de pegar. */
sealed class LicenseValidationResult {
    data class Success(val productName: String?) : LicenseValidationResult()
    data class Invalid(val reason: String) : LicenseValidationResult()
    object NetworkError : LicenseValidationResult()
}

/**
 * Guarda localmente la license key de Lemon Squeezy ya validada y decide si
 * alguna función "Pro" de la app está desbloqueada, siguiendo el mismo patrón
 * de SharedPreferences que el resto de repositorios del proyecto
 * (ver SettingsRepository, LyricsRepository).
 *
 * Lemon Squeezy actúa como Merchant of Record: la compra se hace en su
 * checkout externo (fuera de la app, vía [CHECKOUT_URL]) y el usuario recibe
 * la key por correo. Esta clase solo valida esa key contra la API de Lemon
 * Squeezy (LemonSqueezyApi) y recuerda el resultado.
 *
 * IMPORTANTE: por ahora [isProUnlocked] no está atado a ninguna función
 * específica de la app; solo queda lista y expuesta para usarse después.
 */
class LicenseRepository(context: Context) {
    // applicationContext (no la Activity) para evitar retener memoria de más.
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("license", Context.MODE_PRIVATE)

    private val KEY_LICENSE = "license_key"
    private val KEY_STATUS = "license_status"
    private val KEY_PRODUCT_NAME = "license_product_name"
    private val KEY_LAST_ERROR = "license_last_error"

    fun getSavedLicenseKey(): String? = prefs.getString(KEY_LICENSE, null)

    fun getStatus(): LicenseStatus {
        val raw = prefs.getString(KEY_STATUS, LicenseStatus.INACTIVE.name) ?: LicenseStatus.INACTIVE.name
        return try {
            LicenseStatus.valueOf(raw)
        } catch (e: Exception) {
            LicenseStatus.INACTIVE
        }
    }

    fun getProductName(): String? = prefs.getString(KEY_PRODUCT_NAME, null)

    fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    /**
     * true si la cuenta de Google con la que el usuario ya inició sesión en la
     * app (la misma que usa para Drive/YouTube, ver MainActivity) es la del
     * dueño/creador (OWNER_EMAIL). No requiere ninguna license key.
     *
     * Si nadie inició sesión con Google en la app (GoogleSignIn.getLastSignedInAccount
     * devuelve null), esto simplemente da false y no desbloquea nada — el dueño
     * necesitaría iniciar sesión con esa cuenta al menos una vez para que aplique.
     */
    fun isOwnerAccount(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(appContext) ?: return false
        return account.email?.equals(OWNER_EMAIL, ignoreCase = true) == true
    }

    /**
     * true si hay una licencia guardada y su último estado conocido es ACTIVE,
     * O si quien tiene la sesión de Google iniciada en la app es el dueño
     * (ver [isOwnerAccount]) — el dueño nunca necesita comprarse su propia key.
     * Todavía no desbloquea nada en la app: queda lista para usarse cuando
     * se decida qué función específica proteger.
     */
    fun isProUnlocked(): Boolean =
        isOwnerAccount() || (getSavedLicenseKey() != null && getStatus() == LicenseStatus.ACTIVE)

    /** Muestra la key con solo los últimos 4 caracteres visibles, ej. "********-4835". */
    fun maskedKey(): String? {
        val key = getSavedLicenseKey() ?: return null
        if (key.length <= 4) return "*".repeat(key.length)
        val hiddenCount = (key.length - 4).coerceAtMost(16)
        return "*".repeat(hiddenCount) + key.takeLast(4)
    }

    private fun saveActive(key: String, productName: String?) {
        prefs.edit()
            .putString(KEY_LICENSE, key)
            .putString(KEY_STATUS, LicenseStatus.ACTIVE.name)
            .putString(KEY_PRODUCT_NAME, productName)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun saveInvalid(key: String?, reason: String) {
        val editor = prefs.edit()
        if (key != null) editor.putString(KEY_LICENSE, key)
        editor.putString(KEY_STATUS, LicenseStatus.INACTIVE.name)
        editor.putString(KEY_LAST_ERROR, reason)
        editor.apply()
    }

    /**
     * Sin conexión / fallo de red: NO se borra ni se cambia el estado local de
     * la licencia (podría seguir siendo válida, solo no pudimos confirmarlo).
     * Solo se anota el motivo para poder avisar en la UI.
     */
    private fun markErrorKeepingLocalState(reason: String) {
        prefs.edit().putString(KEY_LAST_ERROR, reason).apply()
    }

    /** Quita la licencia guardada por completo (botón "Quitar licencia" en Ajustes). */
    fun clearLicense() {
        prefs.edit()
            .remove(KEY_LICENSE)
            .remove(KEY_STATUS)
            .remove(KEY_PRODUCT_NAME)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    /**
     * Valida una key nueva (la que el usuario acaba de pegar) contra la API de
     * Lemon Squeezy y, si es válida, la guarda como la licencia activa.
     * Debe llamarse desde una corrutina.
     */
    suspend fun validateAndSave(rawKey: String): LicenseValidationResult {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            return LicenseValidationResult.Invalid("Pega tu license key primero.")
        }

        return try {
            val response = LemonSqueezyApi.service.validateLicense(licenseKey = key)
            val body = response.body()

            if (!response.isSuccessful || body == null) {
                val reason = "La key no es válida o ya fue usada."
                saveInvalid(key, reason)
                return LicenseValidationResult.Invalid(reason)
            }

            if (body.valid) {
                val productName = body.meta?.product_name
                saveActive(key, productName)
                LicenseValidationResult.Success(productName)
            } else {
                val reason = reasonFor(body)
                saveInvalid(key, reason)
                LicenseValidationResult.Invalid(reason)
            }
        } catch (e: Exception) {
            LicenseValidationResult.NetworkError
        }
    }

    /**
     * Revalida en segundo plano y en silencio la licencia ya guardada, para
     * detectar si Lemon Squeezy la revocó o reembolsó. Pensada para llamarse
     * una sola vez al abrir la app (ver MainActivity). Si no hay internet o
     * falla la llamada, NO borra ni cambia el estado local: solo deja
     * constancia del error para poder avisarlo si el usuario entra a Ajustes.
     */
    suspend fun revalidateSilently() {
        val key = getSavedLicenseKey() ?: return

        try {
            val response = LemonSqueezyApi.service.validateLicense(licenseKey = key)
            val body = response.body()

            if (!response.isSuccessful || body == null) {
                markErrorKeepingLocalState("No se pudo re-verificar la licencia.")
                return
            }

            if (body.valid) {
                saveActive(key, body.meta?.product_name)
            } else {
                saveInvalid(key, reasonFor(body))
            }
        } catch (e: Exception) {
            markErrorKeepingLocalState("Sin conexión: no se pudo re-verificar la licencia.")
        }
    }

    private fun reasonFor(body: LemonSqueezyValidateResponse): String {
        return when (body.license_key?.status) {
            "expired" -> "Esta licencia ya expiró."
            "disabled" -> "Esta licencia fue desactivada."
            else -> body.error ?: "Esta licencia ya no es válida."
        }
    }

    companion object {
        // Correo del dueño/creador de la app: si la sesión de Google Sign-In
        // activa en el dispositivo coincide con este correo, isProUnlocked()
        // da true automáticamente, sin necesidad de license key.
        const val OWNER_EMAIL = "oomo87284@gmail.com"

        // TODO: reemplaza esto por el link real de tu checkout/producto en
        // Lemon Squeezy (Store > Products > "Get link" o tu propia página de
        // checkout personalizada). Formato típico:
        // https://TU-TIENDA.lemonsqueezy.com/buy/TU-VARIANT-UUID
        const val CHECKOUT_URL = "https://TU-TIENDA.lemonsqueezy.com/buy/TU-VARIANT-UUID"
    }
}