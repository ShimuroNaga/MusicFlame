package com.music.musicflame.data

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
        val section: String,
        val label: String,
        // Precio individual en MXN. Por defecto el mismo de siempre (PRICE_PER_ITEM_MXN);
        // algunos ítems más grandes (ej. el EQ Pro de 10 bandas) tienen un precio propio.
        val priceMxn: Int = PRICE_PER_ITEM_MXN
    )

    val ITEMS: List<Item> = listOf(
        Item("eq_style_mirrored", "Estilos de ecualizador", "Doble espejado"),
        Item("eq_style_wave", "Estilos de ecualizador", "Ondas de agua"),
        Item("eq_style_pulse", "Estilos de ecualizador", "Círculo pulsante"),
        Item("eq_style_particles", "Estilos de ecualizador", "Partículas"),
        Item("eq_style_thin", "Estilos de ecualizador", "Barras finas"),
        Item("eq_style_vu", "Estilos de ecualizador", "VU meter retro"),
        Item("eq_style_oscilloscope", "Estilos de ecualizador", "Osciloscopio"),
        Item("eq_style_skyline", "Estilos de ecualizador", "Ondas concéntricas"),
        Item("eq_style_rain", "Estilos de ecualizador", "Constelación"),

        // "Adaptativo" del ecualizador vuelto a ser gratis (igual que en
        // texto/Now Playing) — no se vende, no tiene id de catálogo.
        Item("eq_color_custom", "Color del ecualizador", "Personalizado"),
        Item("eq_color_rainbow", "Color del ecualizador", "Arcoíris"),

        Item("text_color_rainbow", "Color de texto", "Arcoíris"),

        Item("now_playing_custom", "Color del \"Now Playing\"", "Personalizado"),
        Item("now_playing_rainbow", "Color del \"Now Playing\"", "Arcoíris"),

        Item("vinyl_widget", "Widget vinilo", "Widget completo"),

        Item("lyrics_custom", "Color de letras", "Personalizado"),
        Item("lyrics_rainbow", "Color de letras", "Arcoíris"),

        Item("font_comfortaa", "Tipo de letra", "Comfortaa"),
        Item("font_playfair_display", "Tipo de letra", "Playfair Display"),
        Item("font_orbitron", "Tipo de letra", "Orbitron"),
        Item("font_press_start_2p", "Tipo de letra", "Press Start 2P"),
        Item("font_space_mono", "Tipo de letra", "Space Mono"),

        Item("pro_eq_10band", "Ecualizador PRO", "10 bandas + normalización de volumen", priceMxn = 15)
    )

    val TOTAL_PRICE_MXN: Int = ITEMS.sumOf { it.priceMxn }
}
