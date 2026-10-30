package com.music.musicflame.voice

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Graba el micrófono a un .m4a (AAC 16 kHz, liviano) y expone el nivel para detectar silencio. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    val file: File = File(ctx.cacheDir, "voice_cmd.m4a")

    fun start() {
        file.delete()
        val r = MediaRecorder(ctx)
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(48_000)
        r.setAudioChannels(1)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        rec = r
    }

    /** Amplitud máxima desde la última consulta (0..32767). */
    fun level(): Int = try { rec?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }

    /** Detiene y devuelve el archivo, o null si quedó vacío/demasiado corto. */
    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        return try {
            r.stop(); r.release()
            file.takeIf { it.length() > 1500 }
        } catch (_: Exception) { try { r.release() } catch (_: Exception) {}; null }
    }

    fun cancel() { stop(); file.delete() }
}
