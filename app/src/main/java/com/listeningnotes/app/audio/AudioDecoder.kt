package com.listeningnotes.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Error de decodificación con mensaje apto para mostrar al usuario. */
class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Audio listo para whisper: mono, 16 kHz, float en [-1, 1].
 * [samples] puede ser mayor que el audio real; las muestras válidas son las primeras [count].
 */
class DecodedAudio(val samples: FloatArray, val count: Int) {
    val durationMs: Long get() = count * 1000L / AudioDecoder.TARGET_SAMPLE_RATE
}

/** Decodifica cualquier formato soportado por el sistema (m4a, opus, ogg, mp3, wav...) con MediaExtractor + MediaCodec. */
class AudioDecoder {

    /** Duración en ms leída de las cabeceras, o null si el contenedor no la indica. */
    suspend fun probeDurationMs(file: File): Long? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val index = findAudioTrack(extractor) ?: return@withContext null
            val format = extractor.getTrackFormat(index)
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else null
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    suspend fun decode(file: File, onProgress: (Float) -> Unit): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(file.absolutePath)
            } catch (e: IOException) {
                throw AudioDecodeException("No se pudo leer el archivo: formato no reconocido", e)
            } catch (e: RuntimeException) {
                throw AudioDecodeException("No se pudo leer el archivo: formato no reconocido", e)
            }
            val trackIndex = findAudioTrack(extractor)
                ?: throw AudioDecodeException("El archivo no contiene ninguna pista de audio")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            if (durationUs > MAX_DURATION_US) {
                throw AudioDecodeException("El audio es demasiado largo (máximo ${MAX_DURATION_US / 60_000_000L} minutos)")
            }

            codec = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(format, null, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                throw AudioDecodeException("Este dispositivo no puede decodificar el formato \"$mime\"", e)
            }

            decodeLoop(extractor, codec, format, durationUs, onProgress)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private suspend fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ): DecodedAudio {
        var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
        var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
        var pcmFloat = false
        var resampler = LinearResampler(sampleRate, TARGET_SAMPLE_RATE)

        // Reservamos según la duración conocida para no copiar el array al crecer.
        val estimated = if (durationUs > 0) (durationUs * TARGET_SAMPLE_RATE / 1_000_000L).toInt() + TARGET_SAMPLE_RATE else TARGET_SAMPLE_RATE * 60
        var out = FloatArray(estimated)
        var outCount = 0

        var shorts = ShortArray(0)
        var floats = FloatArray(0)
        var mono = FloatArray(0)

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var lastProgress = -1f
        var emptyOutputs = 0

        while (!outputDone) {
            currentCoroutineContext().ensureActive()

            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        if (durationUs > 0) {
                            val p = (extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f)
                            if (p - lastProgress >= 0.01f) { lastProgress = p; onProgress(p) }
                        }
                        extractor.advance()
                    }
                }
            }

            val outIndex = try {
                codec.dequeueOutputBuffer(info, TIMEOUT_US)
            } catch (e: IllegalStateException) {
                throw AudioDecodeException("Error al decodificar el audio (el archivo puede estar dañado)", e)
            }
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    val newRate = f.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    if (newRate != sampleRate) {
                        sampleRate = newRate
                        resampler = LinearResampler(sampleRate, TARGET_SAMPLE_RATE)
                    }
                    channels = f.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    pcmFloat = f.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                }
                outIndex >= 0 -> {
                    if (info.size > 0) {
                        emptyOutputs = 0
                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.nativeOrder())
                        val sampleCount: Int
                        if (pcmFloat) {
                            sampleCount = info.size / 4
                            if (floats.size < sampleCount) floats = FloatArray(sampleCount)
                            buf.asFloatBuffer().get(floats, 0, sampleCount)
                        } else {
                            sampleCount = info.size / 2
                            if (shorts.size < sampleCount) shorts = ShortArray(sampleCount)
                            buf.asShortBuffer().get(shorts, 0, sampleCount)
                        }
                        val frames = sampleCount / channels
                        if (mono.size < frames) mono = FloatArray(frames)
                        if (pcmFloat) PcmConverter.floatToMono(floats, sampleCount, channels, mono)
                        else PcmConverter.int16ToMono(shorts, sampleCount, channels, mono)

                        val resampled = resampler.process(mono, frames)
                        if (outCount + resampled.size > MAX_SAMPLES) {
                            throw AudioDecodeException("El audio es demasiado largo")
                        }
                        if (outCount + resampled.size > out.size) {
                            out = out.copyOf(maxOf(out.size * 3 / 2, outCount + resampled.size))
                        }
                        System.arraycopy(resampled, 0, out, outCount, resampled.size)
                        outCount += resampled.size
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Si la entrada terminó y el códec no devuelve nada durante mucho rato, abandonamos.
                    if (inputDone && ++emptyOutputs > 500) {
                        throw AudioDecodeException("El decodificador no respondió al terminar el audio")
                    }
                }
            }
        }
        onProgress(1f)

        val tail = resampler.flush()
        if (tail.isNotEmpty() && outCount + tail.size <= out.size) {
            System.arraycopy(tail, 0, out, outCount, tail.size)
            outCount += tail.size
        }

        if (outCount == 0) throw AudioDecodeException("No se pudo extraer audio del archivo")
        return DecodedAudio(out, outCount)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun MediaFormat.intOr(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val TIMEOUT_US = 10_000L
        /** Límite para no agotar la memoria (60 min a 16 kHz float ≈ 230 MB). */
        const val MAX_DURATION_MINUTES = 60
        private const val MAX_DURATION_US = MAX_DURATION_MINUTES * 60L * 1_000_000L
        private const val MAX_SAMPLES = MAX_DURATION_MINUTES * 60 * TARGET_SAMPLE_RATE + TARGET_SAMPLE_RATE * 30
    }
}
