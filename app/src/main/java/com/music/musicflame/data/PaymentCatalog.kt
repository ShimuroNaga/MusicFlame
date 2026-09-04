package com.music.musicflame.data

/**
 * Catálogo único de los 15 ítems cosméticos bloqueados hasta que el usuario
 * pague, y su precio individual (usado SOLO para mostrar en la tabla
 * informativa/preview de Ajustes > Pagos, ver SettingsScreen). El
 * desbloqueo real es "todo o nada": una sola compra/licencia activa
 * (LicenseRepository.isProUnlocked()) abre TODOS los ítems a la vez, sea
 * cual sea lo que el usuario haya marcado en la tabla — Lemon Squeezy no
 * tiene forma nativa de vender sub-conjuntos de un mismo producto.
 *
 * Los candados de verdad (los que de verdad impiden usar cada opción) viven
 * en cada selector/lugar donde se usan (EqualizerStylePickerDialog, los
 * diálogos de color en SettingsScreen, MusicFlameVinylWidgetProvider), todos
 * consultando LicenseRepository.isProUnlocked(). Esta lista es solo para
 * pintar la tabla y calcular el total seleccionado; si algún día cambia el
 * precio o la lista de ítems, este es el único lugar que hay que tocar para
 * que la tabla de Ajustes > Pagos se actualice.
 */
object PaymentCatalog {

    const val PRICE_PER_ITEM_MXN = 5

    data class Item(
        val id: String,
        val section: String,
        val label: String
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

        Item("eq_color_adaptive", "Color del ecualizador", "Adaptativo"),
        Item("eq_color_custom", "Color del ecualizador", "Personalizado"),
        Item("eq_color_rainbow", "Color del ecualizador", "Arcoíris"),

        Item("text_color_rainbow", "Color de texto", "Arcoíris"),

        Item("now_playing_custom", "Color del \"Now Playing\"", "Personalizado"),
        Item("now_playing_rainbow", "Color del \"Now Playing\"", "Arcoíris"),

        Item("vinyl_widget", "Widget vinilo", "Widget completo"),

        Item("lyrics_custom", "Color de letras", "Personalizado"),
        Item("lyrics_rainbow", "Color de letras", "Arcoíris"),

        // NUEVO: tipos de letra para toda la app (ver ui/theme/AppFonts.kt).
        // Roboto (default), Lato, Open Sans, Inter, Asap Sharp y Nunito son
        // gratis y no aparecen acá. El candado real vive en AppFont.isFree /
        // MusicFlameTheme, no en esta lista (esta solo pinta la tabla).
        Item("font_comfortaa", "Tipo de letra", "Comfortaa"),
        Item("font_playfair_display", "Tipo de letra", "Playfair Display"),
        Item("font_orbitron", "Tipo de letra", "Orbitron"),
        Item("font_press_start_2p", "Tipo de letra", "Press Start 2P"),
        Item("font_space_mono", "Tipo de letra", "Space Mono")
    )

    val TOTAL_PRICE_MXN: Int = ITEMS.size * PRICE_PER_ITEM_MXN
}
