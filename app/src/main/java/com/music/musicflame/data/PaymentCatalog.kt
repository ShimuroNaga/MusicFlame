package com.music.musicflame.data

import androidx.annotation.StringRes
import com.music.musicflame.R

/**
 * Catálogo único de los ítems cosméticos vendidos por separado en Lemon
 * Squeezy, con su precio individual (usado para mostrar en la tabla
 * informativa/preview de Ajustes > Pagos, ver SettingsScreen).
 *
 * NOTA sobre "Adaptativo": es SIEMPRE gratis en los 3 selectores de color
 * (texto, Now Playing y ecualizador) — por eso no hay eq_color_adaptive/
 * text_color_adaptive/now_playing_adaptive en esta lista. El código de
 * SettingsScreen.kt (comentario junto al diálogo "Color del ecualizador")
 * decía lo contrario para ese selector en particular, pero es intencional
 * ignorarlo: el usuario confirmó que Adaptativo debe ser gratis ahí también,
 * así que ese comentario/candado quedó desactualizado y hay que sacarlo del
 * diálogo correspondiente en SettingsScreen.kt.
 *
 * Los candados de verdad (los que de verdad impiden usar cada opción) viven
 * en cada selector/lugar donde se usan (EqualizerStylePickerDialog, los
 * diálogos de color en SettingsScreen, MusicFlameVinylWidgetProvider), cada
 * uno consultando el desbloqueo específico de su ítem en LicenseRepository
 * (no un solo isProUnlocked global). Esta lista es solo para pintar la
 * tabla y calcular el total; si algún día cambia el precio o la lista de
 * ítems, este es el único lugar que hay que tocar para que la tabla de
 * Ajustes > Pagos se actualice.
 */
object PaymentCatalog {

    const val PRICE_PER_ITEM_MXN = 5

    data class Item(
        val id: String,
        @StringRes val sectionRes: Int,
        // Nombre propio que no se traduce (ej. nombres de fuentes); si hay labelRes, manda labelRes.
        val label: String = "",
        @StringRes val labelRes: Int? = null,
        // Precio individual en MXN. Por defecto el mismo de siempre (PRICE_PER_ITEM_MXN);
        // algunos ítems más grandes (ej. el EQ Pro de 10 bandas) tienen un precio propio.
        val priceMxn: Int = PRICE_PER_ITEM_MXN
    ) {
        fun displayLabel(context: android.content.Context): String =
            labelRes?.let { context.getString(it) } ?: label
    }

    val ITEMS: List<Item> = listOf(
        Item("eq_style_mirrored", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_mirrored),
        Item("eq_style_wave", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_wave),
        Item("eq_style_pulse", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_pulse),
        Item("eq_style_particles", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_particles),
        Item("eq_style_thin", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_thin),
        Item("eq_style_vu", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_vu),
        Item("eq_style_oscilloscope", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_osc),
        Item("eq_style_skyline", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_skyline),
        Item("eq_style_rain", R.string.pay_sec_eq_styles, labelRes = R.string.pay_it_rain),

        // "Adaptativo" del ecualizador vuelto a ser gratis (igual que en
        // texto/Now Playing) — no se vende, no tiene id de catálogo.
        Item("eq_color_custom", R.string.pay_sec_eq_color, labelRes = R.string.pay_it_custom),
        Item("eq_color_rainbow", R.string.pay_sec_eq_color, labelRes = R.string.pay_it_rainbow),

        Item("text_color_rainbow", R.string.pay_sec_text_color, labelRes = R.string.pay_it_rainbow),

        Item("now_playing_custom", R.string.pay_sec_now_playing, labelRes = R.string.pay_it_custom),
        Item("now_playing_rainbow", R.string.pay_sec_now_playing, labelRes = R.string.pay_it_rainbow),

        Item("vinyl_widget", R.string.pay_sec_vinyl, labelRes = R.string.pay_it_widget_full),

        Item("lyrics_custom", R.string.pay_sec_lyrics_color, labelRes = R.string.pay_it_custom),
        Item("lyrics_rainbow", R.string.pay_sec_lyrics_color, labelRes = R.string.pay_it_rainbow),

        Item("font_comfortaa", R.string.pay_sec_font, label = "Comfortaa"),
        Item("font_playfair_display", R.string.pay_sec_font, label = "Playfair Display"),
        Item("font_orbitron", R.string.pay_sec_font, label = "Orbitron"),
        Item("font_press_start_2p", R.string.pay_sec_font, label = "Press Start 2P"),
        Item("font_space_mono", R.string.pay_sec_font, label = "Space Mono"),

        Item("pro_eq_10band", R.string.pay_sec_pro_eq, labelRes = R.string.pay_it_pro_eq, priceMxn = 20)
    )

    val TOTAL_PRICE_MXN: Int = ITEMS.sumOf { it.priceMxn }
}