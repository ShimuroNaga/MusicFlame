package com.music.musicflame.voice.wake

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/** Sensibilidad del detector: multiplica el umbral base calculado con tus propias grabaciones. */
enum class WakeSensitivity(val id: String, val multiplier: Float) {
    LOW("low", 1.0f),
    MEDIUM("medium", 1.35f),
    HIGH("high", 1.8f);

    companion object {
        fun fromId(id: String?): WakeSensitivity = values().firstOrNull { it.id == id } ?: MEDIUM
    }
}

/**
 * Plantillas de "oye <nombre>": cada una es la secuencia de huellas de UNA grabación tuya. El audio nunca se guarda,
 * solo estos números (no se puede reconstruir tu voz a partir de ellos de forma práctica).
 */
object WakeTemplates {

    class Data(
        /** Nombre de la IA con el que se grabó (si luego lo cambias, hay que volver a grabar). */
        val name: String,
        val templates: List<Array<FloatArray>>,
        /** Umbral base (antes de aplicar la sensibilidad). */
        val base: Float,
        /** Distancia media entre tus propias grabaciones: más alta = grabaciones menos parecidas entre sí. */
        val spread: Float
    )

    private const val FILE_NAME = "wake_templates.json"
    private const val DEFAULT_BASE = 0.05f
    private const val MIN_BASE = 0.035f
    private const val MAX_BASE = 0.09f

    /** Por encima de esto las grabaciones se parecen poco entre sí (se avisa, pero no se bloquea). */
    const val INCONSISTENT_SPREAD = 0.12f

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE_NAME)

    fun hasTemplates(ctx: Context): Boolean = file(ctx).exists()

    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) {} }

    /** Compara nombres sin importar mayúsculas, acentos ni signos. */
    fun normName(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9ñ ]"), "")
            .replace(Regex("\\s+"), " ").trim()

    /** true si hay plantillas Y se grabaron con el nombre actual de la IA. */
    fun isReadyFor(ctx: Context, aiName: String): Boolean {
        val d = load(ctx) ?: return false
        return normName(d.name) == normName(aiName)
    }

    fun load(ctx: Context): Data? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("t")
            val list = ArrayList<Array<FloatArray>>()
            for (i in 0 until arr.length()) {
                val seq = arr.getJSONArray(i)
                list += Array(seq.length()) { k ->
                    val v = seq.getJSONArray(k)
                    FloatArray(v.length()) { d -> v.getDouble(d).toFloat() }
                }
            }
            if (list.isEmpty()) null
            else Data(o.getString("name"), list, o.optDouble("base", DEFAULT_BASE.toDouble()).toFloat(), o.optDouble("spread", 0.0).toFloat())
        } catch (_: Exception) { null }
    }

    fun save(ctx: Context, d: Data) {
        val o = JSONObject()
        o.put("v", 1)
        o.put("name", d.name)
        o.put("base", d.base.toDouble())
        o.put("spread", d.spread.toDouble())
        val arr = JSONArray()
        for (t in d.templates) {
            val seq = JSONArray()
            for (v in t) {
                val row = JSONArray()
                for (x in v) row.put(Math.round(x * 100000f) / 100000.0)
                seq.put(row)
            }
            arr.put(seq)
        }
        o.put("t", arr)
        file(ctx).writeText(o.toString())
    }

    /**
     * Arma los datos finales y calcula el umbral base con "dejar una fuera": cada grabación se compara contra las demás.
     * Si tu voz varía mucho entre tomas, el umbral sube solo; si eres muy constante, baja.
     */
    fun build(name: String, takes: List<Array<FloatArray>>): Data {
        val dists = ArrayList<Float>()
        for (i in takes.indices) for (j in takes.indices) {
            if (i == j) continue
            val s = WakeDtw.score(takes[i], takes[j].asList())
            if (s < 1f) dists += s
        }
        val spread = if (dists.isEmpty()) DEFAULT_BASE else dists.average().toFloat()
        return Data(name, takes, spread.coerceIn(MIN_BASE, MAX_BASE), spread)
    }

    /** Huellas de una grabación completa (cada 80 ms una, desde que hay ~0.8 s de audio). */
    fun embedClip(extractor: WakeFeatureExtractor, clip: ShortArray): Array<FloatArray> {
        extractor.reset()
        val out = ArrayList<FloatArray>()
        val chunk = ShortArray(WakeFeatureExtractor.CHUNK)
        var off = 0
        while (off + chunk.size <= clip.size) {
            System.arraycopy(clip, off, chunk, 0, chunk.size)
            extractor.feed(chunk)?.let { out += it }
            off += chunk.size
        }
        return out.toTypedArray()
    }
}
