package com.listeningnotes.app.whisper

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Segmento crudo devuelto por whisper (tiempos en ms). */
data class RawSegment(val startMs: Long, val endMs: Long, val text: String)

class WhisperException(message: String) : Exception(message)

/**
 * Envoltorio Kotlin sobre [WhisperNative]. Una sola transcripción a la vez;
 * el contexto nativo se crea y se libera dentro de cada llamada para no retener ~200 MB de RAM.
 */
class WhisperEngine(private val context: Context) {

    @Volatile
    private var handle: Long = 0L

    /** Solicita cancelar la transcripción en curso (el bucle nativo la aborta en cuanto puede). */
    fun cancel() {
        val h = handle
        if (h != 0L) WhisperNative.cancel(h)
    }

    suspend fun transcribe(
        modelFile: File,
        samples: FloatArray,
        sampleCount: Int,
        threads: Int,
        progressCallback: (Int) -> Unit,
    ): List<RawSegment> = withContext(Dispatchers.Default) {
        ensureBackends()
        if (!modelFile.isFile) throw WhisperException("El modelo no está descargado")

        val ctx = WhisperNative.initContext(modelFile.absolutePath)
        if (ctx == 0L) {
            throw WhisperException("No se pudo cargar el modelo (fichero dañado o memoria insuficiente)")
        }
        handle = ctx
        try {
            val rc = WhisperNative.transcribe(
                ctx, samples, sampleCount, "en", threads,
                object : NativeProgressListener {
                    override fun onProgress(percent: Int) = progressCallback(percent)
                },
            )
            if (WhisperNative.wasCancelled(ctx)) throw CancellationException("Transcripción cancelada")
            if (rc != 0) throw WhisperException("Whisper terminó con error (código $rc)")

            val n = WhisperNative.getSegmentCount(ctx)
            buildList {
                for (i in 0 until n) {
                    val text = SegmentCleaner.clean(WhisperNative.getSegmentText(ctx, i)) ?: continue
                    add(RawSegment(WhisperNative.getSegmentT0(ctx, i), WhisperNative.getSegmentT1(ctx, i), text))
                }
            }
        } finally {
            handle = 0L
            WhisperNative.free(ctx)
        }
    }

    private fun ensureBackends() {
        synchronized(WhisperEngine::class.java) {
            if (backendsLoaded) return
            val count = WhisperNative.initBackends(context.applicationInfo.nativeLibraryDir)
            if (count <= 0) {
                throw WhisperException(
                    "No se encontró ningún backend de CPU de ggml. " +
                        "Recompila con whisperCpuVariants=false (ver README)."
                )
            }
            backendsLoaded = true
        }
    }

    private companion object {
        var backendsLoaded = false
    }
}
