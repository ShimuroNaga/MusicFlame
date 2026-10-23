package com.music.musicflame.data

import android.content.Context
import androidx.annotation.StringRes
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
    val catalogIds: List<String>,
    // Nombre traducible que se le muestra al usuario (tabla de Pagos, compras restauradas).
    // Si es null se usa [name].
    @StringRes val nameRes: Int? = null,
    // Precio real del producto en Ko-fi (MXN). null = todavía no definido: se muestra la suma de
    // los priceMxn de sus ítems de PaymentCatalog (ver LicenseRepository.productPriceMxn).
    val priceMxn: Int? = null
) {
    fun displayName(context: Context): String = nameRes?.let { context.getString(it) } ?: name

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
        val label = recognized.joinToString(" · ") { it.displayName(appContext) }
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
            "20992c096c091b93349b2e998a9aa71ce742c0b199f4a218ad0542c88b8e5ee6",
            "15ecd07fd85f35e09c30fe5d7605bf97bb69b34aaf26e587fc555bb3abdc51a4",
            "9126ec76adad1b7f2c99a53836f1b2be1e056b0570cf8938579b6d14f1a934eb"
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
            KofiProduct("TODO", "https://ko-fi.com/s/1be1b91e12", listOf(ALL_ITEMS_ID), nameRes = R.string.lic_whole_catalog, priceMxn = 130),
            KofiProduct(
                "Tipos de letra (5)", "https://ko-fi.com/s/31810100a9",
                listOf("font_comfortaa", "font_playfair_display", "font_orbitron", "font_press_start_2p", "font_space_mono"),
                nameRes = R.string.kofi_prod_fonts, priceMxn = 25
            ),
            KofiProduct(
                "Pack 1 de ecualizadores", "https://ko-fi.com/s/c213993c41",
                listOf("eq_style_mirrored_wave", "eq_style_wave", "eq_style_pulse", "eq_style_particles", "eq_style_thin"),
                nameRes = R.string.kofi_prod_eq_pack1, priceMxn = 25
            ),
            KofiProduct(
                "Pack 2 de ecualizadores", "https://ko-fi.com/s/2a390656e4",
                listOf("eq_style_vu", "eq_style_oscilloscope", "eq_style_skyline", "eq_style_rain", "eq_style_fight"),
                nameRes = R.string.kofi_prod_eq_pack2, priceMxn = 25
            ),
            KofiProduct(
                "Pack de colores", "https://ko-fi.com/s/a9aa209b1e",
                listOf(
                    "eq_color_custom", "eq_color_rainbow", "text_color_rainbow", "now_playing_custom",
                    "now_playing_rainbow", "lyrics_custom", "lyrics_rainbow"
                ),
                nameRes = R.string.kofi_prod_colors, priceMxn = 25
            ),
            KofiProduct("EQ PRO", "https://ko-fi.com/s/e7684d4d2d", listOf("pro_eq_10band"), nameRes = R.string.kofi_prod_pro_eq, priceMxn = 20),
            KofiProduct("Widget vinilo", "https://ko-fi.com/s/c0e9a4d156", listOf("vinyl_widget"), nameRes = R.string.kofi_prod_vinyl, priceMxn = 10)
        )

        /** Producto de Ko-fi que vende [catalogId] (el que lo desbloquea), o null si ninguno lo cubre. */
        fun productFor(catalogId: String): KofiProduct? =
            KOFI_PRODUCTS.firstOrNull { p -> p.catalogIds.none { it == ALL_ITEMS_ID } && catalogId in p.catalogIds }

        /**
         * Precio en MXN que se le muestra al usuario para [product]: el precio real si ya se definió
         * en [KofiProduct.priceMxn]; si no, la suma de los priceMxn de sus ítems en PaymentCatalog
         * (TODO = total del catálogo). Los precios reales de Ko-fi ya están en KOFI_PRODUCTS (en MXN).
         */
        fun productPriceMxn(product: KofiProduct): Int =
            product.priceMxn ?: if (product.catalogIds.contains(ALL_ITEMS_ID)) {
                PaymentCatalog.TOTAL_PRICE_MXN
            } else {
                PaymentCatalog.ITEMS.filter { it.id in product.catalogIds }.sumOf { it.priceMxn }
            }

        /** Precio mostrado del producto "TODO". */
        fun buyAllPriceMxn(): Int =
            KOFI_PRODUCTS.firstOrNull { it.catalogIds.contains(ALL_ITEMS_ID) }?.let { productPriceMxn(it) }
                ?: PaymentCatalog.TOTAL_PRICE_MXN

        /** Link del producto "TODO" (desbloquea el catálogo completo de una), o null si todavía no existe. */
        fun buyAllUrl(): String? = KOFI_PRODUCTS.firstOrNull { it.catalogIds.contains(ALL_ITEMS_ID) }?.url

        /** Link de compra del producto que vende [catalogId], o null si no se encontró o aún no existe en Ko-fi. */
        fun checkoutUrlFor(catalogId: String): String? =
            KOFI_PRODUCTS.firstOrNull { catalogId in it.catalogIds }?.url
    }
}
