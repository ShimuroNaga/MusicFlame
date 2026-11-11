/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en la mitad determinista de packages/blobatar/src/render.ts y en el
 * port a Dart. Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 *
 * La pose y el movimiento por tiempo viven en sus propios archivos (parte 3).
 */
package com.music.musicflame.ui.blobatar

/** Cómo se dibuja el fondo. Equivale a `false | "square" | "circle" | "squircle"` de JS. */
enum class Backdrop { NONE, SQUARE, CIRCLE, SQUIRCLE }

/**
 * Las opciones que leen el layout y la paleta.
 *
 * @property palette Sustituye entradas concretas de la paleta. Lo sustituido se salta la garantía de contraste.
 * @property hue Fija el matiz en grados, para que el nombre solo gobierne la forma.
 * @property tone Fija el tono como posición 0-1 en el juego de muestras (un `1` exacto cae en la primera; usar 0.999 para tinta).
 * @property traits Fija rasgos individuales: cada valor es la posición 0-1 que habría dado el hash (número o lista de números). Se recortan.
 * @property normalize Aplica NFC + trim + minúsculas al nombre. Por defecto true.
 * @property contrast Exige las razones mínimas de contraste. Por defecto true.
 * @property background El fondo que dibuja el renderizador. Null = el del estilo (en gen-2, ninguno).
 * @property backgroundColor Color del fondo `#rgb`/`#rrggbb`; conserva la garantía de contraste. `palette["bg"]` gana si van ambos.
 */
data class BlobatarOptions(
    val palette: Map<String, String>? = null,
    val hue: Double? = null,
    val tone: Double? = null,
    val traits: Map<String, Any>? = null,
    val normalize: Boolean = true,
    val contrast: Boolean = true,
    val background: Backdrop? = null,
    val backgroundColor: String? = null
)

/** Las entradas ya resueltas: el lector de rasgos y la paleta final. */
class Resolved(val t: Traits, val palette: MutableMap<String, String>)

/** La placa detrás de la figura, como geometría y no como markup. */
class BackdropGeometry(val path: BlobPath, val fill: String)

object BlobatarRender {
    /** Estilo por defecto de Blobatar 2. */
    private val style get() = BlobatarStyles.style

    /**
     * Normaliza y hashea el nombre una vez, arma el lector de rasgos y resuelve la paleta (con las unidades
     * amigables `hue`/`tone` y los overrides de paleta). `hue`/`tone` ganan a las entradas de `traits`.
     *
     * [styleDefault] es el fondo propio del estilo: `backgroundColor` solo entra a la cadena de contraste si
     * de verdad se dibuja una placa.
     */
    fun resolve(seed: String, opts: BlobatarOptions = BlobatarOptions(), styleDefault: Backdrop = Backdrop.NONE): Resolved {
        val bg = opts.backgroundColor
        require(bg.isNullOrEmpty() || BlobatarColor.plateHex(bg) != null) {
            "backgroundColor debe ser #rgb o #rrggbb: $bg"
        }
        val plated = (opts.background ?: styleDefault) != Backdrop.NONE
        val t = Traits.forSeed(seed, opts.normalize, opts.traits)
        val p = BlobatarColor.palette(
            opts.hue ?: t.numIn("hue", 0.0, 360.0),
            opts.contrast,
            opts.tone ?: t("tone"),
            if (plated && !bg.isNullOrEmpty()) bg else null
        )
        opts.palette?.let { p.putAll(it) }
        return Resolved(t, p)
    }

    /** El fondo es asunto del estilo, no del renderizador. */
    fun backdropFor(background: Backdrop?, p: Map<String, String>, styleDefault: Backdrop = Backdrop.NONE): BackdropGeometry? {
        val fill = p[BlobatarColor.BG]!!
        return when (background ?: styleDefault) {
            Backdrop.NONE -> null
            // `M0 0H100V100H0Z`: box(50, 50, 50, 50) emite exactamente eso.
            Backdrop.SQUARE -> BackdropGeometry(BlobatarShapes.box(50.0, 50.0, 50.0, 50.0), fill)
            Backdrop.CIRCLE -> BackdropGeometry(
                BlobatarShapes.superellipse(Superellipse(50.0, 50.0, 50.0, 50.0, 2.0)), fill
            )
            Backdrop.SQUIRCLE -> BackdropGeometry(
                BlobatarShapes.superellipse(Superellipse(50.0, 50.0, 50.0, 50.0, 6.0)), fill
            )
        }
    }

    /** El layout numérico completo de un nombre. */
    fun layoutFor(name: String, opts: BlobatarOptions = BlobatarOptions()): BlobatarLayout =
        style.layout(resolve(name, opts).t)

    /** Layout y paleta juntos, para quien dibuja. */
    fun partsFor(name: String, opts: BlobatarOptions = BlobatarOptions()): Pair<BlobatarLayout, Map<String, String>> {
        val r = resolve(name, opts)
        return style.layout(r.t) to r.palette
    }
}
