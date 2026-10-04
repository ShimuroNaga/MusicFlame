package com.music.musicflame.data

import com.music.musicflame.ui.utils.CoverShapeGeometry
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Figura base de un diseño de carátula personalizado.
 *
 * @param sidesLabel nombre de lo que cuenta el slider de cantidad (null = la figura no lo usa).
 * @param depthLabel nombre de lo que controla el slider de profundidad (null = no lo usa).
 */
enum class CoverFigure(val label: String, val sidesLabel: String?, val depthLabel: String?) {
    POLYGON("Polígono", "Caras", null),
    STAR("Estrella", "Puntas", "Profundidad de las puntas"),
    FLOWER("Flor", "Pétalos", "Profundidad de los pétalos"),
    HEART("Corazón", null, null),
    CROSS("Cruz", null, "Grosor de los brazos"),
    GEAR("Engrane", "Dientes", "Altura de los dientes"),
    DIAMOND("Rombo", null, "Anchura")
}

/**
 * Diseño de carátula creado por el usuario en Ajustes > Apariencia >
 * Forma de la carátula > "+ Crear diseño".
 *
 * @param figure Polígono, estrella, flor, corazón, cruz, engrane o rombo.
 * @param sides Caras (polígono), puntas (estrella), pétalos (flor) o dientes (engrane).
 * @param roundness 0 = esquinas vivas, 1 = redondeo máximo (cualquier polígono queda circular).
 * @param transparency 0 = carátula opaca; MAX_TRANSPARENCY = la más transparente permitida.
 * @param depth 0..1: profundidad de puntas/pétalos, grosor de la cruz, altura de los dientes
 *        o anchura del rombo (según [figure]). 0.5 reproduce el aspecto de los diseños viejos.
 * @param rotation Giro de la figura en grados (0..360), sentido de las manecillas del reloj.
 */
data class CoverDesign(
    val id: String = newId(),
    val name: String = DEFAULT_NAME,
    val figure: CoverFigure = CoverFigure.POLYGON,
    val sides: Int = 6,
    val roundness: Float = 0.3f,
    val transparency: Float = 0f,
    val depth: Float = DEFAULT_DEPTH,
    val rotation: Float = 0f
) {
    /** Opacidad lista para aplicar (alpha) sobre la carátula. */
    val opacity: Float get() = 1f - transparency

    /**
     * Copia con todos los valores dentro de rango. Se aplica siempre que se lee un
     * diseño del disco: un archivo de configuración importado (ConfigExportRepository)
     * o editado a mano nunca puede dejar valores que rompan el dibujo.
     */
    fun sanitized(): CoverDesign = copy(
        name = name.trim().take(MAX_NAME_LENGTH).ifEmpty { DEFAULT_NAME },
        sides = sides.coerceIn(CoverShapeGeometry.MIN_SIDES, CoverShapeGeometry.MAX_SIDES),
        roundness = if (roundness.isNaN()) 0f else roundness.coerceIn(0f, 1f),
        transparency = if (transparency.isNaN()) 0f else transparency.coerceIn(0f, MAX_TRANSPARENCY),
        depth = if (depth.isNaN()) DEFAULT_DEPTH else depth.coerceIn(0f, 1f),
        rotation = if (rotation.isNaN() || rotation.isInfinite()) 0f else ((rotation % 360f) + 360f) % 360f
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("figure", figure.name)
        put("sides", sides)
        put("roundness", roundness.toDouble())
        put("transparency", transparency.toDouble())
        put("depth", depth.toDouble())
        put("rotation", rotation.toDouble())
    }

    companion object {
        const val DEFAULT_NAME = "Mi diseño"
        const val MAX_NAME_LENGTH = 24
        const val MAX_TRANSPARENCY = 0.8f
        const val DEFAULT_DEPTH = 0.5f

        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(obj: JSONObject): CoverDesign? {
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val figure = try {
                CoverFigure.valueOf(obj.optString("figure", CoverFigure.POLYGON.name))
            } catch (e: Exception) {
                CoverFigure.POLYGON
            }
            return CoverDesign(
                id = id,
                name = obj.optString("name", DEFAULT_NAME),
                figure = figure,
                sides = obj.optInt("sides", 6),
                roundness = obj.optDouble("roundness", 0.3).toFloat(),
                transparency = obj.optDouble("transparency", 0.0).toFloat(),
                // Diseños guardados antes de existir estos campos: se leen con los valores neutros.
                depth = obj.optDouble("depth", DEFAULT_DEPTH.toDouble()).toFloat(),
                rotation = obj.optDouble("rotation", 0.0).toFloat()
            ).sanitized()
        }

        fun listToJson(designs: List<CoverDesign>): String {
            val arr = JSONArray()
            designs.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        /** Tolera null, texto vacío o JSON dañado (devuelve lista vacía) y descarta ids repetidos. */
        fun listFromJson(json: String?): List<CoverDesign> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val result = ArrayList<CoverDesign>()
                for (i in 0 until arr.length()) {
                    val design = arr.optJSONObject(i)?.let { fromJson(it) } ?: continue
                    if (result.none { it.id == design.id }) result.add(design)
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
