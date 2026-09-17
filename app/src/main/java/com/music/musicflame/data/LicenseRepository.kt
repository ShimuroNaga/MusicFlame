package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class LicenseStatus { INACTIVE, ACTIVE, ERROR }

/** Resultado de validar una key nueva que el usuario acaba de pegar. */
sealed class LicenseValidationResult {
    /** [unlockedLabel] es lo que se le muestra al usuario ("Doble espejado + Ondas de agua", "Todo el catálogo", etc). */
    data class Success(val productName: String?, val unlockedLabel: String) : LicenseValidationResult()
    data class Invalid(val reason: String) : LicenseValidationResult()
    object AlreadyAdded : LicenseValidationResult()
    object NetworkError : LicenseValidationResult()
}

/**
 * Una compra ya validada contra Lemon Squeezy: su key, el nombre del producto
 * que Lemon Squeezy devolvió (meta.product_name) y qué ids de PaymentCatalog
 * desbloquea esa key en particular (puede ser más de uno, ej. "Doble
 * espejado y Ondas de agua" desbloquea eq_style_mirrored + eq_style_wave).
 */
data class SavedLicense(
    val key: String,
    val productName: String?,
    val status: String, // LicenseStatus.name
    val unlockedItemIds: List<String>,
    val lastError: String? = null
)

/**
 * Guarda localmente TODAS las license keys de Lemon Squeezy ya validadas por
 * el usuario y decide, ítem por ítem del catálogo (ver PaymentCatalog), si
 * está desbloqueado — siguiendo el mismo patrón de SharedPreferences+JSON
 * (org.json, sin dependencias nuevas) que el resto de repositorios del
 * proyecto (ver ArtworkCacheRepository, VolumeNormalizationCacheRepository).
 *
 * REEMPLAZA el modelo anterior de "una sola license key = todo o nada":
 * ahora en Lemon Squeezy cada ítem (o grupo chico de 2 ítems, agrupados solo
 * por precio mínimo) es un producto separado con su propia key, más un
 * producto "TODO" que las desbloquea todas de una. El usuario puede terminar
 * con varias keys guardadas a la vez (una por cada compra que hizo), por eso
 * ahora se guarda una LISTA en vez de una sola.
 *
 * Lemon Squeezy actúa como Merchant of Record: la compra se hace en su
 * checkout externo (fuera de la app, vía [checkoutUrlFor]/[CHECKOUT_URL_TODO])
 * y el usuario recibe la key por correo, la pega en Ajustes > Pagos.
 */
class LicenseRepository(context: Context) {
    // applicationContext (no la Activity) para evitar retener memoria de más.
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("license", Context.MODE_PRIVATE)

    private val KEY_LICENSES = "licenses_json"

    // Nombres de las claves legacy (modelo viejo de "una sola licencia"), solo
    // para migrar una key que ya estuviera guardada de antes a la lista nueva
    // la primera vez que se lea, y no perderla.
    private val LEGACY_KEY_LICENSE = "license_key"
    private val LEGACY_KEY_STATUS = "license_status"
    private val LEGACY_KEY_PRODUCT_NAME = "license_product_name"

    // ---------------------------------------------------------------------
    // Lectura/escritura de la lista de licencias guardadas
    // ---------------------------------------------------------------------

    fun getSavedLicenses(): List<SavedLicense> {
        migrateLegacyLicenseIfNeeded()
        val json = prefs.getString(KEY_LICENSES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val idsArr = obj.optJSONArray("unlockedItemIds")
                val ids = mutableListOf<String>()
                if (idsArr != null) {
                    for (j in 0 until idsArr.length()) ids.add(idsArr.getString(j))
                }
                SavedLicense(
                    key = obj.getString("key"),
                    productName = obj.optString("productName", null).takeIf { obj.has("productName") && !obj.isNull("productName") },
                    status = obj.optString("status", LicenseStatus.INACTIVE.name),
                    unlockedItemIds = ids,
                    lastError = obj.optString("lastError", null).takeIf { obj.has("lastError") && !obj.isNull("lastError") }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLicenses(list: List<SavedLicense>) {
        val arr = JSONArray()
        list.forEach { lic ->
            val obj = JSONObject()
            obj.put("key", lic.key)
            obj.put("productName", lic.productName)
            obj.put("status", lic.status)
            obj.put("unlockedItemIds", JSONArray(lic.unlockedItemIds))
            obj.put("lastError", lic.lastError)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_LICENSES, arr.toString()).apply()
    }

    /** Migra la key única del modelo viejo (si había alguna) a la lista nueva, una sola vez. */
    private fun migrateLegacyLicenseIfNeeded() {
        if (prefs.contains(KEY_LICENSES)) return
        val legacyKey = prefs.getString(LEGACY_KEY_LICENSE, null) ?: run {
            // No había nada que migrar: igual dejamos la lista vacía marcada,
            // para no repetir este chequeo en cada lectura.
            prefs.edit().putString(KEY_LICENSES, JSONArray().toString()).apply()
            return
        }
        val legacyStatus = prefs.getString(LEGACY_KEY_STATUS, LicenseStatus.INACTIVE.name) ?: LicenseStatus.INACTIVE.name
        val legacyProductName = prefs.getString(LEGACY_KEY_PRODUCT_NAME, null)
        val unlockedIds = resolveUnlockedIds(legacyProductName)
        val migrated = SavedLicense(legacyKey, legacyProductName, legacyStatus, unlockedIds)
        saveLicenses(listOf(migrated))
        prefs.edit()
            .remove(LEGACY_KEY_LICENSE)
            .remove(LEGACY_KEY_STATUS)
            .remove(LEGACY_KEY_PRODUCT_NAME)
            .remove("license_last_error")
            .apply()
    }

    // ---------------------------------------------------------------------
    // Cuenta dueña (sin cambios de comportamiento)
    // ---------------------------------------------------------------------

    /**
     * true si la cuenta de Google con la que el usuario ya inició sesión en la
     * app (la misma que usa para Drive/YouTube, ver MainActivity) es la del
     * dueño/creador, o cualquier otro correo autorizado (comparados por hash
     * SHA-256, ver AUTHORIZED_EMAIL_SHA256_SET). No requiere ninguna license key.
     */
    fun isOwnerAccount(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(appContext) ?: return false
        val email = account.email ?: return false
        return sha256(email.trim().lowercase()) in AUTHORIZED_EMAIL_SHA256_SET
    }

    /**
     * true SOLO si la cuenta de Google activa es la del dueño/creador real
     * (oomo) — a diferencia de [isOwnerAccount], que acepta cualquier correo
     * de [AUTHORIZED_EMAIL_SHA256_SET]. Usar específicamente para gatear la
     * edición de la Meta de ahorro.
     */
    fun canEditSavingsGoal(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(appContext) ?: return false
        val email = account.email ?: return false
        return sha256(email.trim().lowercase()) in SAVINGS_GOAL_EDITOR_EMAIL_SHA256_SET
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---------------------------------------------------------------------
    // Desbloqueo por ítem (REEMPLAZA a isProUnlocked() "todo o nada")
    // ---------------------------------------------------------------------

    /**
     * true si el ítem [catalogId] (un id de PaymentCatalog.ITEMS) está
     * desbloqueado: porque el usuario es el dueño, o porque tiene guardada
     * al menos una licencia ACTIVA que lo cubre (directamente, o porque
     * compró "TODO"). Este es el chequeo que hay que usar en cada picker
     * (estilo de ecualizador, color, tipografía, widget, EQ Pro) en vez del
     * viejo isProUnlocked() global.
     */
    fun isItemUnlocked(catalogId: String): Boolean {
        if (isOwnerAccount()) return true
        return getSavedLicenses().any { lic ->
            lic.status == LicenseStatus.ACTIVE.name &&
                (lic.unlockedItemIds.contains(ALL_ITEMS_ID) || lic.unlockedItemIds.contains(catalogId))
        }
    }

    /** Todos los ids de PaymentCatalog actualmente desbloqueados (dueño = todos). Útil para pintar la UI de una sola pasada en vez de llamar isItemUnlocked() ítem por ítem. */
    fun unlockedItemIds(): Set<String> {
        if (isOwnerAccount()) return PaymentCatalog.ITEMS.map { it.id }.toSet()
        val ids = mutableSetOf<String>()
        getSavedLicenses().forEach { lic ->
            if (lic.status == LicenseStatus.ACTIVE.name) {
                if (lic.unlockedItemIds.contains(ALL_ITEMS_ID)) {
                    ids.addAll(PaymentCatalog.ITEMS.map { it.id })
                } else {
                    ids.addAll(lic.unlockedItemIds)
                }
            }
        }
        return ids
    }

    /**
     * true si el dueño, o si tiene AL MENOS una licencia activa de lo que
     * sea. Solo sirve para casos generales tipo "¿tiene algo comprado?"
     * (ej. mostrar una sección "Tus compras" no vacía) — NO usar para
     * gatear un ítem específico, para eso está [isItemUnlocked].
     */
    fun hasAnyActiveLicense(): Boolean =
        isOwnerAccount() || getSavedLicenses().any { it.status == LicenseStatus.ACTIVE.name }

    /** Muestra una key con solo los últimos 4 caracteres visibles, ej. "********-4835". */
    fun mask(key: String): String {
        if (key.length <= 4) return "*".repeat(key.length)
        val hiddenCount = (key.length - 4).coerceAtMost(16)
        return "*".repeat(hiddenCount) + key.takeLast(4)
    }

    /** Nombre corto para mostrarle al usuario qué desbloquea una licencia ("Todo el catálogo", "Doble espejado + Ondas de agua", etc). */
    fun labelFor(unlockedItemIds: List<String>): String {
        if (unlockedItemIds.contains(ALL_ITEMS_ID)) return "Todo el catálogo"
        return unlockedItemIds
            .mapNotNull { id -> PaymentCatalog.ITEMS.find { it.id == id }?.label }
            .joinToString(" + ")
            .ifBlank { "(producto no reconocido)" }
    }

    // ---------------------------------------------------------------------
    // Validar/agregar/quitar licencias
    // ---------------------------------------------------------------------

    /**
     * Valida una key nueva (la que el usuario acaba de pegar) contra la API
     * de Lemon Squeezy y, si es válida, la AGREGA a la lista de licencias
     * guardadas (no reemplaza las que ya había — el usuario puede ir
     * comprando ítems de a poco). Debe llamarse desde una corrutina.
     */
    suspend fun validateAndAdd(rawKey: String): LicenseValidationResult {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            return LicenseValidationResult.Invalid("Pega tu license key primero.")
        }

        val existing = getSavedLicenses()
        if (existing.any { it.key == key }) {
            return LicenseValidationResult.AlreadyAdded
        }

        return try {
            val response = LemonSqueezyApi.service.validateLicense(licenseKey = key)
            val body = response.body()

            if (!response.isSuccessful || body == null) {
                return LicenseValidationResult.Invalid("La key no es válida o ya fue usada.")
            }

            if (body.valid) {
                val productName = body.meta?.product_name
                val unlockedIds = resolveUnlockedIds(productName)
                val newLicense = SavedLicense(key, productName, LicenseStatus.ACTIVE.name, unlockedIds)
                saveLicenses(existing + newLicense)
                if (unlockedIds.isEmpty()) {
                    // La key es real y quedó guardada, pero no reconocimos el
                    // nombre del producto contra el mapa interno (ver
                    // PRODUCT_NAME_TO_CATALOG_IDS) — no debería pasar si el
                    // catálogo y Lemon Squeezy están sincronizados, pero así
                    // no se pierde la compra ni se le miente al usuario.
                    LicenseValidationResult.Invalid(
                        "La licencia es válida (\"$productName\") pero no se reconoce qué desbloquea todavía. Avisale al desarrollador."
                    )
                } else {
                    LicenseValidationResult.Success(productName, labelFor(unlockedIds))
                }
            } else {
                LicenseValidationResult.Invalid(reasonFor(body))
            }
        } catch (e: Exception) {
            LicenseValidationResult.NetworkError
        }
    }

    /** Quita una licencia guardada en particular (botón "Quitar" al lado de cada key en Ajustes). */
    fun removeLicense(key: String) {
        saveLicenses(getSavedLicenses().filterNot { it.key == key })
    }

    /** Quita TODAS las licencias guardadas. */
    fun clearAllLicenses() {
        prefs.edit().remove(KEY_LICENSES).apply()
    }

    /**
     * Revalida en segundo plano y en silencio TODAS las licencias guardadas,
     * para detectar si Lemon Squeezy revocó o reembolsó alguna. Pensada para
     * llamarse una sola vez al abrir la app (ver MainActivity). Si no hay
     * internet o falla una llamada puntual, esa licencia NO se borra ni se
     * marca inválida: solo queda constancia del error para poder avisarlo en
     * Ajustes, igual que antes.
     */
    suspend fun revalidateAllSilently() {
        val current = getSavedLicenses()
        if (current.isEmpty()) return

        val updated = current.map { lic ->
            try {
                val response = LemonSqueezyApi.service.validateLicense(licenseKey = lic.key)
                val body = response.body()
                when {
                    !response.isSuccessful || body == null ->
                        lic.copy(lastError = "No se pudo re-verificar la licencia.")
                    body.valid -> {
                        val productName = body.meta?.product_name ?: lic.productName
                        lic.copy(
                            status = LicenseStatus.ACTIVE.name,
                            productName = productName,
                            unlockedItemIds = resolveUnlockedIds(productName).ifEmpty { lic.unlockedItemIds },
                            lastError = null
                        )
                    }
                    else -> lic.copy(status = LicenseStatus.INACTIVE.name, lastError = reasonFor(body))
                }
            } catch (e: Exception) {
                lic.copy(lastError = "Sin conexión: no se pudo re-verificar la licencia.")
            }
        }
        saveLicenses(updated)
    }

    private fun resolveUnlockedIds(productName: String?): List<String> {
        if (productName == null) return emptyList()
        return PRODUCT_NAME_TO_CATALOG_IDS[productName.trim()] ?: emptyList()
    }

    private fun reasonFor(body: LemonSqueezyValidateResponse): String {
        return when (body.license_key?.status) {
            "expired" -> "Esta licencia ya expiró."
            "disabled" -> "Esta licencia fue desactivada."
            else -> body.error ?: "Esta licencia ya no es válida."
        }
    }

    companion object {
        // Hashes SHA-256 (correo en minúsculas, sin espacios) de las cuentas
        // con Pro desbloqueado sin necesidad de license key.
        val AUTHORIZED_EMAIL_SHA256_SET = setOf(
            "1b397423ec27150c85d54a05c5ec7d9138156bbab614ba973a90d86449b293f8",
            "35a83b8adf17dbf05c1158eb4d1e53eded0bb5e842bdefc0ded8568986ad40ed"
        )

        // Subconjunto de lo de arriba: SOLO el dueño real (oomo), para gatear
        // la edición de la Meta de ahorro.
        val SAVINGS_GOAL_EDITOR_EMAIL_SHA256_SET = setOf(
            "1b397423ec27150c85d54a05c5ec7d9138156bbab614ba973a90d86449b293f8"
        )

        /**
         * Id especial: si una licencia lo trae en su [SavedLicense.unlockedItemIds],
         * desbloquea TODO PaymentCatalog.ITEMS (el producto "TODO" de Lemon Squeezy).
         */
        const val ALL_ITEMS_ID = "__ALL__"

        /**
         * Mapa product_name (tal cual lo devuelve Lemon Squeezy en
         * meta.product_name — es decir, el campo "Name" de cada producto en
         * el dashboard, copiado EXACTO incluyendo mayúsculas/typos) -> ids
         * del catálogo (PaymentCatalog) que desbloquea.
         *
         * OJO: si renombrás un producto en Lemon Squeezy, este mapa se rompe
         * en silencio para ese producto (la key sigue siendo válida, pero
         * resolveUnlockedIds no encuentra nada — validateAndAdd sí avisa en
         * ese caso, ver arriba). Hay que mantenerlos sincronizados a mano.
         *
         * "tipo de letra...": nombre completo del producto confirmado por el usuario:
         * "tipo de letra(texto confortaa,playfair display,orbitron,press start 2p y space mono)".
         */
        val PRODUCT_NAME_TO_CATALOG_IDS: Map<String, List<String>> = mapOf(
            "TODO" to listOf(ALL_ITEMS_ID),
            "tipo de letra(texto confortaa,playfair display,orbitron,press start 2p y space mono)" to listOf(
                "font_comfortaa", "font_playfair_display", "font_orbitron", "font_press_start_2p", "font_space_mono"
            ),
            "Osciloscopio y Ondas Concetricas" to listOf("eq_style_oscilloscope", "eq_style_skyline"),
            "Doble espejado y Ondas de agua" to listOf("eq_style_mirrored", "eq_style_wave"),
            "ECUALIZADOR PRO y constelacion" to listOf("pro_eq_10band", "eq_style_rain"),
            "Color de texto y now playing(ARCOIRIS Y PERSONALIZADO)" to listOf("text_color_rainbow", "now_playing_custom"),
            "Color de Now playing(Arcoiris) y Widget Vinil" to listOf("now_playing_rainbow", "vinyl_widget"),
            "Color de lyrics(personalizado y arcoiris)" to listOf("lyrics_custom", "lyrics_rainbow"),
            "Color de ecualizador (PERSONALIZADO Y ARCOIRIS)" to listOf("eq_color_custom", "eq_color_rainbow"),
            "Circulo pulsante y Particulas" to listOf("eq_style_pulse", "eq_style_particles"),
            "Barras Finas y VU meter retro" to listOf("eq_style_thin", "eq_style_vu")
        )

        /** Mismas keys que [PRODUCT_NAME_TO_CATALOG_IDS]: link de checkout de Lemon Squeezy de cada producto. */
        val PRODUCT_NAME_TO_CHECKOUT_URL: Map<String, String> = mapOf(
            "TODO" to "https://musicflame.lemonsqueezy.com/checkout/buy/861c3777-c6cd-4a54-94ef-0b2304e3d6d7",
            "tipo de letra(texto confortaa,playfair display,orbitron,press start 2p y space mono)" to "https://musicflame.lemonsqueezy.com/checkout/buy/79cd2bae-40e2-436e-bb5c-5e5271e8ab92",
            "Osciloscopio y Ondas Concetricas" to "https://musicflame.lemonsqueezy.com/checkout/buy/49cb6444-796b-43e2-8543-f2986d45c231",
            "Doble espejado y Ondas de agua" to "https://musicflame.lemonsqueezy.com/checkout/buy/67a45e8c-814d-4459-a025-421fb163ff10",
            "ECUALIZADOR PRO y constelacion" to "https://musicflame.lemonsqueezy.com/checkout/buy/ac81dfe7-4b4d-4efb-900d-9c1c5fd26009",
            "Color de texto y now playing(ARCOIRIS Y PERSONALIZADO)" to "https://musicflame.lemonsqueezy.com/checkout/buy/8254b9b1-86ce-4d71-bc59-1cd6049b5669",
            "Color de Now playing(Arcoiris) y Widget Vinil" to "https://musicflame.lemonsqueezy.com/checkout/buy/99f9f8cc-09c8-48ea-9b56-84a5935b7548",
            "Color de lyrics(personalizado y arcoiris)" to "https://musicflame.lemonsqueezy.com/checkout/buy/ef2e271b-e8a5-4506-9346-8bd0aa9d49a5",
            "Color de ecualizador (PERSONALIZADO Y ARCOIRIS)" to "https://musicflame.lemonsqueezy.com/checkout/buy/3419cc0f-4c27-418c-bfaf-3fc75c86e5e7",
            "Circulo pulsante y Particulas" to "https://musicflame.lemonsqueezy.com/checkout/buy/869c15c3-d9c4-4a57-aa35-6efd54586570",
            "Barras Finas y VU meter retro" to "https://musicflame.lemonsqueezy.com/checkout/buy/34def065-4734-4314-9818-ff6f74722cf2"
        )

        /**
         * Dado un id del catálogo (PaymentCatalog), devuelve el link de
         * checkout de Lemon Squeezy del producto que lo vende, o null si no
         * se encontró (no debería pasar si PaymentCatalog y el mapa de
         * arriba están sincronizados).
         */
        fun checkoutUrlFor(catalogId: String): String? {
            val productName = PRODUCT_NAME_TO_CATALOG_IDS.entries
                .firstOrNull { (_, ids) -> catalogId in ids }
                ?.key ?: return null
            return PRODUCT_NAME_TO_CHECKOUT_URL[productName]
        }

        /** Checkout del producto "TODO" (desbloquea el catálogo completo de una). */
        const val CHECKOUT_URL_TODO = "https://musicflame.lemonsqueezy.com/checkout/buy/861c3777-c6cd-4a54-94ef-0b2304e3d6d7"
    }
}
