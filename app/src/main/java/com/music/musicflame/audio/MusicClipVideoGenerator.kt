package com.music.musicflame.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Genera un video corto y vertical (mp4) para compartir un fragmento de una canción en
 * CUALQUIER app (Instagram, WhatsApp, Telegram, X, TikTok, etc.): carátula estática de fondo
 * + el audio real de la canción horneado adentro. No depende de ninguna integración oficial
 * de terceros (eso solo lo tienen partners de streaming con licencia, tipo Spotify) — al ser
 * un video normal con sonido, cualquier app lo reproduce solo con el selector genérico de
 * Android (Intent.ACTION_SEND).
 *
 * Todo con APIs nativas de Android (MediaExtractor/MediaCodec/MediaMuxer), sin ninguna
 * librería externa — mismo criterio que VolumeNormalizationAnalyzer y el resto del pipeline
 * de audio del proyecto (restricción de licencia: nada de FFmpeg/TarsosDSP/etc.).
 */
object MusicClipVideoGenerator {

    private const val VIDEO_WIDTH = 720
    private const val VIDEO_HEIGHT = 1280
    // Carátula estática: no hace falta más que un par de fps, así el archivo pesa poco y
    // sigue siendo un video "de verdad" que cualquier player entiende sin rarezas.
    private const val VIDEO_FPS = 2
    private const val VIDEO_BITRATE = 2_500_000
    private const val VIDEO_I_FRAME_INTERVAL = 1

    private const val AUDIO_BITRATE = 128_000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * @param durationMs cuánto dura el fragmento (se recorta solo si la canción es más
     *   corta que eso).
     * @return el archivo mp4 generado en cache/share_clips/, o null si algo falló (archivo
     *   corrupto, formato de audio no soportado por el decoder del celular, etc.) — en ese
     *   caso el llamador simplemente avisa que no se pudo generar, sin tronar la app.
     */
    fun generate(context: Context, songPath: String, durationMs: Long = 30_000L): File? {
        return try {
            val pcm = decodeAudioClip(songPath, durationMs) ?: return null
            val albumArt = loadAlbumArtOrPlaceholder(songPath)

            val outputDir = File(context.cacheDir, "share_clips").apply { mkdirs() }
            // Limpiamos clips viejos de sesiones anteriores antes de generar el nuevo, para
            // no ir acumulando videos de varios MB en el caché sin que el usuario lo note.
            outputDir.listFiles()?.forEach { it.delete() }
            val outputFile = File(outputDir, "clip_${System.currentTimeMillis()}.mp4")

            val audioTrack = encodeAudioTrack(pcm)
            val videoTrack = encodeVideoTrack(albumArt, pcm.durationUs)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val audioTrackIndex = muxer.addTrack(audioTrack.format)
            val videoTrackIndex = muxer.addTrack(videoTrack.format)
            muxer.start()

            audioTrack.samples.forEach { muxer.writeSampleData(audioTrackIndex, it.buffer, it.info) }
            videoTrack.samples.forEach { muxer.writeSampleData(videoTrackIndex, it.buffer, it.info) }

            muxer.stop()
            muxer.release()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ---------- DECODIFICAR EL FRAGMENTO DE AUDIO A PCM ----------

    private class PcmClip(
        val data: ShortArray,
        val sampleCount: Int,
        val sampleRate: Int,
        val channelCount: Int
    ) {
        val durationUs: Long
            get() = (sampleCount.toLong() / channelCount) * 1_000_000L / sampleRate
    }

    private fun decodeAudioClip(filePath: String, durationMs: Long): PcmClip? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxSamples = (durationMs * sampleRate / 1000L).toInt() * channelCount
            val pcmBuffer = ShortArray(maxSamples)
            var writeIndex = 0

            var sawInputEos = false
            var sawOutputEos = false
            val bufferInfo = MediaCodec.BufferInfo()
            val maxUs = durationMs * 1000L

            while (!sawOutputEos && writeIndex < maxSamples) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > maxUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        codec.getOutputBuffer(outputIndex)?.let { outBuffer ->
                            outBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            while (outBuffer.remaining() >= 2 && writeIndex < maxSamples) {
                                pcmBuffer[writeIndex++] = outBuffer.short
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (writeIndex == 0) return null

            // Recortamos al último frame COMPLETO (todos los canales), para no desalinear
            // izquierdo/derecho si el corte cayó a la mitad de un frame estéreo.
            val trimmed = writeIndex - (writeIndex % channelCount)
            if (trimmed == 0) return null
            return PcmClip(pcmBuffer, trimmed, sampleRate, channelCount)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            try { codec?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }

    // ---------- RESULTADO COMÚN DE UN TRACK CODIFICADO ----------

    private class EncodedSample(val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)
    private class EncodedTrack(val format: MediaFormat, val samples: List<EncodedSample>)

    // ---------- RE-CODIFICAR EL PCM DEL FRAGMENTO A AAC ----------

    private fun encodeAudioTrack(pcm: PcmClip): EncodedTrack {
        val configFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(configFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val samples = mutableListOf<EncodedSample>()
        var outputFormat: MediaFormat = configFormat
        val bufferInfo = MediaCodec.BufferInfo()

        val bytesPerSample = 2
        val totalBytes = pcm.sampleCount * bytesPerSample
        val bytesPerFrame = pcm.channelCount * bytesPerSample
        val usPerByte = 1_000_000.0 / (pcm.sampleRate * bytesPerFrame)

        val sourceBytes = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (i in 0 until pcm.sampleCount) putShort(pcm.data[i])
            rewind()
        }

        var byteOffset = 0
        var presentationTimeUs = 0L
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                    val chunkSize = min(inputBuffer.capacity(), totalBytes - byteOffset)
                    if (chunkSize <= 0) {
                        encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        inputBuffer.clear()
                        sourceBytes.position(byteOffset)
                        val slice = sourceBytes.slice()
                        slice.limit(chunkSize)
                        inputBuffer.put(slice)
                        encoder.queueInputBuffer(inputIndex, 0, chunkSize, presentationTimeUs, 0)
                        byteOffset += chunkSize
                        presentationTimeUs = (byteOffset * usPerByte).toLong()
                    }
                }
            }

            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = encoder.outputFormat
                }
                outputIndex >= 0 -> {
                    // El primer buffer de un encoder AAC suele venir marcado CODEC_CONFIG
                    // (el header ASC) — ese YA quedó incluido en outputFormat de arriba, así
                    // que no se escribe como sample o el muxer lo duplicaría.
                    if (bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)) {
                        samples.add(copySample(encoder, outputIndex, bufferInfo))
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()
        return EncodedTrack(outputFormat, samples)
    }

    // ---------- CODIFICAR EL VIDEO (CARÁTULA ESTÁTICA REPETIDA) ----------

    private fun encodeVideoTrack(bitmap: Bitmap, durationUs: Long): EncodedTrack {
        val configFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(configFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // OJO: createInputSurface() va SIEMPRE antes de start(), si no truena.
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val samples = mutableListOf<EncodedSample>()
        var outputFormat: MediaFormat = configFormat
        val bufferInfo = MediaCodec.BufferInfo()

        val frameDurationUs = 1_000_000L / VIDEO_FPS
        val totalFrames = (durationUs / frameDurationUs).toInt().coerceAtLeast(1)

        // Contador de frames REALMENTE codificados (no de frames empujados al Surface), para
        // asignarles nosotros el timestamp (0, frameDurationUs, 2*frameDurationUs, ...) en vez
        // de dejar que el Surface use System.nanoTime() (tiempo desde que prendió el celular)
        // como PTS — eso era lo que causaba que el video reportara una duración absurda tipo
        // "119:18:17" en vez de coincidir con el audio, que sí arranca bien en 0.
        val videoFrameCounter = intArrayOf(0)

        repeat(totalFrames) {
            val canvas = inputSurface.lockCanvas(null)
            drawFrame(canvas, bitmap)
            inputSurface.unlockCanvasAndPost(canvas)
            drainVideoEncoder(encoder, samples, bufferInfo, drainToEos = false, frameDurationUs, videoFrameCounter) { outputFormat = it }
        }

        encoder.signalEndOfInputStream()
        drainVideoEncoder(encoder, samples, bufferInfo, drainToEos = true, frameDurationUs, videoFrameCounter) { outputFormat = it }

        encoder.stop()
        encoder.release()
        inputSurface.release()

        return EncodedTrack(outputFormat, samples)
    }

    private fun drainVideoEncoder(
        encoder: MediaCodec,
        samples: MutableList<EncodedSample>,
        bufferInfo: MediaCodec.BufferInfo,
        drainToEos: Boolean,
        frameDurationUs: Long,
        frameCounter: IntArray,
        onFormatChanged: (MediaFormat) -> Unit
    ) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (drainToEos) continue else return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged(encoder.outputFormat)
                }
                outputIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        // Ignoramos el PTS real que trae el Surface (System.nanoTime(), tiempo
                        // desde que prendió el celular) y le ponemos el nuestro, uno por frame
                        // realmente codificado, para que el track de video quede alineado a 0
                        // igual que el de audio.
                        val ownPtsUs = frameCounter[0].toLong() * frameDurationUs
                        frameCounter[0]++
                        samples.add(copySample(encoder, outputIndex, bufferInfo, overridePtsUs = ownPtsUs))
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    if (!drainToEos) return
                }
            }
        }
    }

    private fun copySample(
        encoder: MediaCodec,
        outputIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
        overridePtsUs: Long? = null
    ): EncodedSample {
        val outBuffer = encoder.getOutputBuffer(outputIndex)!!
        outBuffer.position(bufferInfo.offset)
        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
        val copy = ByteBuffer.allocate(bufferInfo.size)
        copy.put(outBuffer)
        copy.rewind()
        val infoCopy = MediaCodec.BufferInfo().apply {
            set(0, bufferInfo.size, overridePtsUs ?: bufferInfo.presentationTimeUs, bufferInfo.flags)
        }
        return EncodedSample(copy, infoCopy)
    }

    private fun drawFrame(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(Color.BLACK)
        // "Cubrir" el marco vertical con la carátula (que casi siempre es cuadrada) sin
        // deformarla: se recorta el sobrante arriba/abajo en vez de estirarla.
        val scale = maxOf(VIDEO_WIDTH.toFloat() / bitmap.width, VIDEO_HEIGHT.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (VIDEO_WIDTH - drawWidth) / 2f
        val top = (VIDEO_HEIGHT - drawHeight) / 2f
        val dest = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    // ---------- CARÁTULA (con placeholder si la canción no tiene) ----------

    private fun loadAlbumArtOrPlaceholder(songPath: String): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(songPath)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                if (bitmap != null) return bitmap
            }
        } catch (e: Exception) {
            // Sin carátula embebida o archivo no legible por MediaMetadataRetriever: cae al
            // placeholder de abajo, nunca truena por esto.
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        val size = 720
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1E1E1E"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4CAF93")
            textSize = size * 0.35f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("\u266A", size / 2f, size / 2f + paint.textSize / 3f, paint)
        return bitmap
    }
}