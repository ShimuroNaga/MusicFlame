package com.music.musicflame.audio

import android.content.Context
import android.content.Intent
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.data.SettingsStore
import java.text.Normalizer

/**
 * Ecualizador de 5 bandas controlado por voz. SOLO acepta una lista cerrada: los presets del EQ (los de la app
 * y los que guardó el usuario) y subir/bajar una de las 5 bandas. Guarda y avisa al servicio igual que el botón
 * "Aplicar" de Ajustes > Studio Pro EQ. Los presets base se repiten aquí porque en Ajustes viven dentro de la UI.
 */
object EqVoice {
    class Snapshot(val preset: String, val bands: List<Float>, val bass: Float, val virt: Float, val loud: Float, val reverb: Int)

    private val BASE: Map<String, List<Float>> = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(0.5f, 0.3f, -0.1f, 0.3f, 0.5f),
        "Pop" to listOf(-0.1f, 0.2f, 0.4f, 0.2f, -0.1f),
        "Hip hop" to listOf(0.7f, 0.4f, 0f, 0.2f, 0.4f),
        "Jazz" to listOf(0.3f, 0.2f, -0.1f, 0.2f, 0.4f),
        "Classical" to listOf(0.4f, 0.3f, -0.1f, 0.3f, 0.4f),
        "Electronico" to listOf(0.6f, 0.4f, -0.1f, 0.4f, 0.6f),
        "Refuerzo de graves" to listOf(0.9f, 0.5f, 0f, 0f, 0f),
        "Refuerzo de agudos" to listOf(0f, 0f, 0f, 0.5f, 0.9f),
        "Vocales" to listOf(-0.2f, 0f, 0.6f, 0.4f, -0.1f)
    ) + EqPresets.FREE_BANDS

    /** Nombres de bandas que puede usar la IA, de graves a agudos. */
    val BAND_IDS = listOf("bass", "low_mid", "mid", "high_mid", "treble")
    const val STEP = 0.2f

    private val RE_MARKS = Regex("\\p{M}+")
    private fun fold(s: String) = RE_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").filter { it.isLetterOrDigit() || it == ' ' }.trim()

    private fun customNames(ctx: Context): List<String> =
        (SettingsStore.get(ctx).getString("custom_preset_names", "") ?: "").split(",").filter { it.isNotBlank() }

    /** Empareja lo que dijo el usuario con un preset real (exacto y luego contenido). null = no existe. */
    fun findPreset(ctx: Context, query: String?): String? {
        val q = fold(query ?: return null)
        if (q.isEmpty()) return null
        val all = BASE.keys.toList() + customNames(ctx)
        all.firstOrNull { fold(it) == q }?.let { return it }
        return all.filter { fold(it).let { n -> n.contains(q) || q.contains(n) } }.maxByOrNull { fold(it).length }
    }

    fun snapshot(ctx: Context): Snapshot {
        val p = SettingsStore.get(ctx)
        return Snapshot(
            SettingsRepository(ctx).getEqPresetSelected(), List(5) { p.getFloat("eq_band_$it", 0f) },
            p.getFloat("bass_boost", 0f), p.getFloat("virtualizer", 0f), p.getFloat("loudness_enhancer", 0f), p.getInt("reverb_preset", 0)
        )
    }

    /** Guarda todo y avisa al servicio de reproducción (mismo broadcast que el botón "Aplicar"). */
    fun apply(ctx: Context, s: Snapshot) {
        val repo = SettingsRepository(ctx)
        repo.saveEqPresetSelected(s.preset)
        for (i in 0 until 5) repo.saveEqBand(i, s.bands[i])
        repo.saveBassBoost(s.bass); repo.saveVirtualizer(s.virt)
        SettingsStore.get(ctx).edit().putFloat("loudness_enhancer", s.loud).putInt("reverb_preset", s.reverb).apply()
        ctx.sendBroadcast(Intent("com.music.musicflame.UPDATE_EQ").apply {
            setPackage(ctx.packageName)
            putExtra("bass_boost", s.bass); putExtra("virtualizer", s.virt)
            putExtra("loudness", s.loud); putExtra("reverb", s.reverb)
            for (i in 0 until 5) putExtra("eq_band_$i", s.bands[i])
        })
    }

    /** Aplica un preset como lo hace Ajustes. Devuelve su nombre real, o null si no existe. */
    fun applyPreset(ctx: Context, query: String?): String? {
        val name = findPreset(ctx, query) ?: return null
        val cur = snapshot(ctx)
        val base = BASE[name]
        val next = if (base != null) {
            val flat = name == "Flat"
            Snapshot(
                name, base,
                bass = when { flat -> 0f; name == "Refuerzo de graves" -> 100f; else -> EqPresets.FREE_BASS[name] ?: cur.bass },
                virt = if (flat) 0f else cur.virt, loud = if (flat) 0f else cur.loud, reverb = if (flat) 0 else cur.reverb
            )
        } else {
            val p = SettingsStore.get(ctx)
            val saved = (p.getString("preset_${name}_bands", "") ?: "").split(",").mapNotNull { it.toFloatOrNull() }
            Snapshot(
                name, if (saved.size == 5) saved else cur.bands,
                p.getFloat("preset_${name}_bass", 0f), p.getFloat("preset_${name}_virt", 0f),
                p.getFloat("preset_${name}_loud", 0f), p.getInt("preset_${name}_reverb", 0)
            )
        }
        apply(ctx, next)
        return name
    }

    /** Sube o baja una banda un paso. Devuelve su índice (0..4), o null si el nombre no es de la lista. */
    fun nudgeBand(ctx: Context, bandId: String?, up: Boolean): Int? {
        val idx = BAND_IDS.indexOf(bandId?.trim()?.lowercase())
        if (idx < 0) return null
        val cur = snapshot(ctx)
        val bands = cur.bands.toMutableList()
        bands[idx] = (bands[idx] + if (up) STEP else -STEP).coerceIn(-1f, 1f)
        apply(ctx, Snapshot("Customizar", bands, cur.bass, cur.virt, cur.loud, cur.reverb))
        return idx
    }
}
