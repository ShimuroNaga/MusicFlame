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

        Item("eq_color_adaptive", "Color del ecualizador", "Adaptativo"),
        Item("eq_color_custom", "Color del ecualizador", "Personalizado"),
        Item("eq_color_rainbow", "Color del ecualizador", "Arcoíris"),

        Item("text_color_rainbow", "Color de texto", "Arcoíris"),

        Item("now_playing_custom", "Color del \"Now Playing\"", "Personalizado"),
        Item("now_playing_rainbow", "Color del \"Now Playing\"", "Arcoíris"),

        Item("vinyl_widget", "Widget vinilo", "Widget completo"),

        Item("lyrics_custom", "Color de letras", "Personalizado"),
        Item("lyrics_rainbow", "Color de letras", "Arcoíris")
    )

    val TOTAL_PRICE_MXN: Int = ITEMS.size * PRICE_PER_ITEM_MXN
}
