package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONArray
import java.security.MessageDigest
import com.music.musicflame.R

/**
 * Un producto de la tienda de Ko-fi y qué ítems del catálogo (PaymentCatalog) desbloquea.
 *
 * [url] es el link del producto en Ko-fi (https://ko-fi.com/s/xxxxxxxxxx). null = el producto
 * todavía no está creado en Ko-fi: su botón de compra queda deshabilitado. [code] es la parte
 * final del link (el direct_link_code que Ko-fi manda en el webhook).
 */
data class KofiProduct(
    val name: String,
    val url: String?,
    val catalogIds: List<String>
) {
    val code: String?
        get() = url
            ?.substringAfter("/s/", "")
            ?.substringBefore('?')
            ?.substringBefore('/')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/** Resultado de "Restaurar compras". */
sealed class RestoreResult {
    /** [unlockedLabel] es lo que se le muestra al usuario ("Doble espejado + Ondas de agua", "Todo el catálogo", etc). */
    data class Success(val unlockedLabel: String) : RestoreResult()
    /** Hay compras en ese correo pero ninguna coincide con un producto conocido de la app. */
    object Unrecognized : RestoreResult()
    object NothingFound : RestoreResult()
    object NeedEmail : RestoreResult()
    object NetworkError : RestoreResult()
}

/**
 * Decide, ítem por ítem del catálogo (ver PaymentCatalog), si está desbloqueado.
 *
 * Las compras se hacen en la tienda de Ko-fi (fuera de la app). Ko-fi avisa por webhook a
 * Supabase (ver supabase/functions/kofi-webhook) y la app consulta ahí, con el correo de la
 * cuenta de Google (o el que el usuario escriba), qué productos compró ([KofiPurchasesApi]).
 * Lo restaurado se guarda localmente (SharedPreferences, mismo patrón que otros repositorios)
 * para que los candados funcionen sin internet. Es solo aditivo: restaurar nunca quita compras.
 */
class LicenseRepository(context: Context) {
    // applicationContext (no la Activity) para evitar retener memoria de más.
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("license", Context.MODE_PRIVATE)

    private val KEY_CODES = "kofi_codes_json"
    private val KEY_EMAIL = "kofi_restore_email"

    // ---------------------------------------------------------------------
    // Compras guardadas en este dispositivo
    // ---------------------------------------------------------------------

    /** Códigos (direct_link_code) de los productos de Ko-fi ya restaurados en este dispositivo. */
    fun getPurchasedCodes(): Set<String> {
        val json = prefs.getString(KEY_CODES, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun savePurchasedCodes(codes: Set<String>) {
        prefs.edit().putString(KEY_CODES, JSONArray(codes.toList()).toString()).apply()
    }

    /** Correo que el usuario escribió a mano la última vez que restauró con éxito (null si nunca lo hizo). */
    fun getRestoreEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Productos conocidos de la app que este dispositivo ya tiene comprados. */
    fun recognizedProducts(): List<KofiProduct> {
        val purchased = getPurchasedCodes()
        return KOFI_PRODUCTS.filter { p ->
            val c = p.code
            c != null && c in purchased
        }
    }

    /** Borra las compras restauradas de este dispositivo (no afecta lo comprado en Ko-fi: se puede volver a restaurar). */
    fun clearPurchases() {
        prefs.edit().remove(KEY_CODES).remove(KEY_EMAIL).apply()
    }

    // ---------------------------------------------------------------------
    // Cuenta dueña (sin cambios de comportamiento)
    // ---------------------------------------------------------------------

    /**
     * true si la cuenta de Google con la que el usuario ya inició sesión en la
     * app (la misma que usa para Drive/YouTube, ver MainActivity) es la del
     * dueño/creador, o cualquier otro correo autorizado (comparados por hash
     * SHA-256, ver AUTHORIZED_EMAIL_SHA256_SET). No requiere comprar nada.
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

    /**
     * true SOLO para el dueño real. Gatea lo que únicamente él debe ver en
     * Ajustes (Meta de ahorro editable, uso de Supabase). Las demás cuentas de
     * [AUTHORIZED_EMAIL_SHA256_SET] tienen Pro pero NO ven nada de esto.
     */
    fun isRealOwnerAccount(): Boolean = canEditSavingsGoal()

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun googleEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(appContext)?.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    // ---------------------------------------------------------------------
    // Desbloqueo por ítem
    // ---------------------------------------------------------------------

    /** Todos los ids de PaymentCatalog actualmente desbloqueados (dueño = todos). */
    fun unlockedItemIds(): Set<String> {
        val all = PaymentCatalog.ITEMS.map { it.id }.toSet()
        if (isOwnerAccount()) return all
        val ids = mutableSetOf<String>()
        for (product in recognizedProducts()) {
            if (product.catalogIds.contains(ALL_ITEMS_ID)) return all
            ids.addAll(product.catalogIds)
        }
        return ids
    }

    /**
     * true si el ítem [catalogId] (un id de PaymentCatalog.ITEMS) está desbloqueado: porque el
     * usuario es el dueño, o porque tiene comprado un producto que lo cubre (directamente, o
     * porque compró "TODO"). Es el chequeo que usa cada picker (estilo de ecualizador, color,
     * tipografía, widget, EQ Pro).
     */
    fun isItemUnlocked(catalogId: String): Boolean = catalogId in unlockedItemIds()

    /**
     * true si el dueño, o si tiene AL MENOS una compra reconocida. Solo para casos generales tipo
     * "¿tiene algo comprado?" — NO usar para gatear un ítem específico, para eso está [isItemUnlocked].
     */
    fun hasAnyActiveLicense(): Boolean = isOwnerAccount() || recognizedProducts().isNotEmpty()

    /** Nombre corto para mostrarle al usuario qué desbloquea una compra ("Todo el catálogo", "Doble espejado + Ondas de agua", etc). */
    fun labelFor(unlockedItemIds: List<String>): String {
        if (unlockedItemIds.contains(ALL_ITEMS_ID)) return appContext.getString(R.string.lic_whole_catalog)
        return unlockedItemIds
            .mapNotNull { id -> PaymentCatalog.ITEMS.find { it.id == id }?.displayLabel(appContext) }
            .joinToString(" + ")
            .ifBlank { appContext.getString(R.string.lic_unrecognized_product) }
    }

    // ---------------------------------------------------------------------
    // Restaurar compras
    // ---------------------------------------------------------------------

    /**
     * Consulta a Supabase las compras de: el correo escrito a mano ([manualEmail]), el último
     * correo manual que funcionó, y el de la cuenta de Google con sesión iniciada. Las
     * compras encontradas se SUMAN a las que ya había. Debe llamarse desde una corrutina.
     */
    suspend fun restorePurchases(manualEmail: String?): RestoreResult {
        val typed = manualEmail?.trim()?.lowercase().orEmpty()
        val emails = linkedSetOf<String>()
        if (typed.isNotEmpty()) emails.add(typed)
        getRestoreEmail()?.takeIf { it.isNotBlank() }?.let { emails.add(it) }
        googleEmail()?.let { emails.add(it) }
        if (emails.isEmpty()) return RestoreResult.NeedEmail

        val found = mutableSetOf<String>()
        var anyOk = false
        for (email in emails) {
            when (val r = KofiPurchasesApi.fetchPurchasedCodes(email)) {
                is KofiPurchasesApi.Result.Ok -> { anyOk = true; found.addAll(r.codes) }
                KofiPurchasesApi.Result.Failure -> Unit
            }
        }
        if (!anyOk) return RestoreResult.NetworkError
        if (found.isEmpty()) return RestoreResult.NothingFound

        savePurchasedCodes(getPurchasedCodes() + found)
        if (typed.isNotEmpty()) prefs.edit().putString(KEY_EMAIL, typed).apply()

        val recognized = recognizedProducts()
        if (recognized.isEmpty()) return RestoreResult.Unrecognized
        val label = recognized.joinToString(" · ") { labelFor(it.catalogIds) }
        return RestoreResult.Success(label)
    }

    /**
     * Igual que [restorePurchases] pero en silencio (sin diálogos ni Toasts), para llamarse una
     * vez al abrir la app (ver MainActivity). Si no hay internet o falla, no toca nada.
     */
    suspend fun restoreSilently() {
        val emails = linkedSetOf<String>()
        getRestoreEmail()?.takeIf { it.isNotBlank() }?.let { emails.add(it) }
        googleEmail()?.let { emails.add(it) }
        if (emails.isEmpty()) return
        val found = mutableSetOf<String>()
        for (email in emails) {
            val r = KofiPurchasesApi.fetchPurchasedCodes(email)
            if (r is KofiPurchasesApi.Result.Ok) found.addAll(r.codes)
        }
        if (found.isNotEmpty()) savePurchasedCodes(getPurchasedCodes() + found)
    }

    companion object {
        // Hashes SHA-256 (correo en minúsculas, sin espacios) de las cuentas
        // con todo desbloqueado sin necesidad de comprar.
        val AUTHORIZED_EMAIL_SHA256_SET = setOf(
            "1b397423ec27150c85d54a05c5ec7d9138156bbab614ba973a90d86449b293f8",
            "35a83b8adf17dbf05c1158eb4d1e53eded0bb5e842bdefc0ded8568986ad40ed",
            "20992c096c091b93349b2e998a9aa71ce742c0b199f4a218ad0542c88b8e5ee6"
        )

        // Subconjunto de lo de arriba: SOLO el dueño real (oomo), para gatear
        // la edición de la Meta de ahorro.
        val SAVINGS_GOAL_EDITOR_EMAIL_SHA256_SET = setOf(
            "1b397423ec27150c85d54a05c5ec7d9138156bbab614ba973a90d86449b293f8"
        )

        /**
         * Id especial: si un producto lo trae en [KofiProduct.catalogIds], desbloquea TODO
         * PaymentCatalog.ITEMS (el producto "TODO" de la tienda de Ko-fi).
         */
        const val ALL_ITEMS_ID = "__ALL__"

        /**
         * Productos de la tienda de Ko-fi. Para cada uno que ya exista, pega en [KofiProduct.url]
         * el link que te da Ko-fi (https://ko-fi.com/s/xxxxxxxxxx). Mientras sea null, su botón
         * de compra queda deshabilitado. El nombre es solo una referencia para ti; lo que
         * identifica la compra es el código del final del link.
         */
        val KOFI_PRODUCTS: List<KofiProduct> = listOf(
            KofiProduct("TODO", null, listOf(ALL_ITEMS_ID)),
            KofiProduct(
                "Tipos de letra (Comfortaa, Playfair Display, Orbitron, Press Start 2P y Space Mono)", null,
                listOf("font_comfortaa", "font_playfair_display", "font_orbitron", "font_press_start_2p", "font_space_mono")
            ),
            KofiProduct("Osciloscopio y Ondas Concéntricas", null, listOf("eq_style_oscilloscope", "eq_style_skyline")),
            KofiProduct("Doble espejado y Ondas de agua", null, listOf("eq_style_mirrored", "eq_style_wave")),
            KofiProduct("ECUALIZADOR PRO y Constelación", null, listOf("pro_eq_10band", "eq_style_rain")),
            KofiProduct("Color de texto y Now Playing (Arcoíris y Personalizado)", null, listOf("text_color_rainbow", "now_playing_custom")),
            KofiProduct("Color de Now Playing (Arcoíris) y Widget Vinilo", null, listOf("now_playing_rainbow", "vinyl_widget")),
            KofiProduct("Color de lyrics (Personalizado y Arcoíris)", null, listOf("lyrics_custom", "lyrics_rainbow")),
            KofiProduct("Color de ecualizador (Personalizado y Arcoíris)", null, listOf("eq_color_custom", "eq_color_rainbow")),
            KofiProduct("Círculo pulsante y Partículas", null, listOf("eq_style_pulse", "eq_style_particles")),
            KofiProduct("Barras Finas y VU meter retro", null, listOf("eq_style_thin", "eq_style_vu")),
            KofiProduct("Pelea de Stickmans", null, listOf("eq_style_fight"))
        )

        /** Link del producto "TODO" (desbloquea el catálogo completo de una), o null si todavía no existe. */
        fun buyAllUrl(): String? = KOFI_PRODUCTS.firstOrNull { it.catalogIds.contains(ALL_ITEMS_ID) }?.url

        /** Link de compra del producto que vende [catalogId], o null si no se encontró o aún no existe en Ko-fi. */
        fun checkoutUrlFor(catalogId: String): String? =
            KOFI_PRODUCTS.firstOrNull { catalogId in it.catalogIds }?.url
    }
}
