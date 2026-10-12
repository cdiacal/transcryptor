package com.listeningnotes.app.whisper

/** Callback invocado desde C++ (hilo de transcripción) con el progreso de whisper (0..100). */
interface NativeProgressListener {
    fun onProgress(percent: Int)
}

/**
 * Declaraciones JNI de `libwhisper_jni.so` (ver app/src/main/cpp/whisper_jni.cpp).
 * Los nombres de las funciones deben coincidir con los símbolos `Java_..._WhisperNative_*`.
 * Los tiempos de los segmentos se devuelven ya en milisegundos.
 */
object WhisperNative {
    init {
        System.loadLibrary("whisper_jni")
    }

    /** Carga las variantes de CPU de ggml (solo útil con whisperCpuVariants=true). Devuelve nº de dispositivos. */
    external fun initBackends(nativeLibDir: String): Int

    /** Devuelve el puntero al contexto, o 0 si el modelo no se pudo cargar. */
    external fun initContext(modelPath: String): Long

    /** 0 = correcto; otro valor = error o cancelación. */
    external fun transcribe(
        ctx: Long,
        samples: FloatArray,
        nSamples: Int,
        language: String,
        threads: Int,
        listener: NativeProgressListener?,
    ): Int

    external fun cancel(ctx: Long)
    external fun wasCancelled(ctx: Long): Boolean
    external fun getSegmentCount(ctx: Long): Int
    external fun getSegmentText(ctx: Long, index: Int): String
    external fun getSegmentT0(ctx: Long, index: Int): Long
    external fun getSegmentT1(ctx: Long, index: Int): Long
    external fun free(ctx: Long)
    external fun systemInfo(): String
}
