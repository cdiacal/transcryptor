package com.listeningnotes.app.audio

import kotlin.math.abs

/** Conversión de PCM a mono float y remuestreo. Sin dependencias de Android (probado con tests unitarios). */
object PcmConverter {

    /**
     * Mezcla a mono promediando canales y normaliza PCM 16-bit a [-1, 1].
     * @param interleaved muestras intercaladas (L R L R...)
     * @param sampleCount número de muestras válidas en [interleaved] (todos los canales)
     * @return número de muestras mono escritas en [out]
     */
    fun int16ToMono(interleaved: ShortArray, sampleCount: Int, channels: Int, out: FloatArray): Int {
        require(channels >= 1) { "channels debe ser >= 1" }
        val frames = sampleCount / channels
        require(out.size >= frames) { "El buffer de salida es demasiado pequeño" }
        val scale = 1f / 32768f
        if (channels == 1) {
            for (i in 0 until frames) out[i] = interleaved[i] * scale
        } else {
            for (i in 0 until frames) {
                var sum = 0
                val base = i * channels
                for (c in 0 until channels) sum += interleaved[base + c]
                out[i] = sum * scale / channels
            }
        }
        return frames
    }

    /** Igual que [int16ToMono] para PCM float (ya normalizado). */
    fun floatToMono(interleaved: FloatArray, sampleCount: Int, channels: Int, out: FloatArray): Int {
        require(channels >= 1) { "channels debe ser >= 1" }
        val frames = sampleCount / channels
        require(out.size >= frames) { "El buffer de salida es demasiado pequeño" }
        if (channels == 1) {
            System.arraycopy(interleaved, 0, out, 0, frames)
        } else {
            for (i in 0 until frames) {
                var sum = 0f
                val base = i * channels
                for (c in 0 until channels) sum += interleaved[base + c]
                out[i] = sum / channels
            }
        }
        return frames
    }
}

/**
 * Remuestreador por interpolación lineal, apto para streaming: se puede alimentar por bloques
 * y el resultado es idéntico a procesar todo el audio de una vez.
 *
 * Mantiene la posición (en unidades de muestra de entrada) de la siguiente muestra de salida y la
 * última muestra del bloque anterior para interpolar entre bloques.
 */
class LinearResampler(inputRate: Int, outputRate: Int) {
    private val step: Double = inputRate.toDouble() / outputRate.toDouble()

    // Posición de la próxima muestra de salida respecto al inicio del bloque actual
    // (índice -1 = última muestra del bloque anterior).
    private var pos = 0.0
    private var prev = 0f
    private var hasData = false

    init {
        require(inputRate > 0 && outputRate > 0) { "Las frecuencias deben ser positivas" }
    }

    /** Procesa las primeras [length] muestras de [input] y devuelve las muestras remuestreadas. */
    fun process(input: FloatArray, length: Int = input.size): FloatArray {
        if (length <= 0) return FloatArray(0)
        val maxOut = ((length - pos) / step).toInt() + 2
        val out = FloatArray(maxOut.coerceAtLeast(0))
        var n = 0
        while (true) {
            val i = Math.floor(pos).toInt()
            if (i + 1 >= length) break
            val a = if (i < 0) prev else input[i]
            val b = input[i + 1]
            val frac = (pos - i).toFloat()
            if (n >= out.size) break // salvaguarda; no debería ocurrir
            out[n++] = a + (b - a) * frac
            pos += step
        }
        pos -= length
        prev = input[length - 1]
        hasData = true
        return if (n == out.size) out else out.copyOf(n)
    }

    /**
     * Emite la última muestra pendiente si la siguiente posición de salida cae exactamente sobre ella
     * (el modo streaming la retiene porque necesita la muestra siguiente para interpolar).
     */
    fun flush(): FloatArray =
        if (hasData && abs(pos + 1.0) < 1e-9) floatArrayOf(prev).also { pos += step } else FloatArray(0)

    companion object {
        /** Remuestreo de un array completo. */
        fun resample(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
            if (inputRate == outputRate) return input.copyOf()
            val r = LinearResampler(inputRate, outputRate)
            return r.process(input) + r.flush()
        }
    }
}
