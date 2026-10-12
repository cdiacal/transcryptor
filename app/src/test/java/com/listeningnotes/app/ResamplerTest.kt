package com.listeningnotes.app

import com.listeningnotes.app.audio.LinearResampler
import com.listeningnotes.app.audio.PcmConverter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class ResamplerTest {

    @Test
    fun `misma frecuencia devuelve la misma senal`() {
        val input = FloatArray(100) { it / 100f }
        assertArrayEquals(input, LinearResampler.resample(input, 16_000, 16_000), 0f)
        // También por el camino de streaming (step = 1.0)
        val r = LinearResampler(16_000, 16_000)
        assertArrayEquals(input, r.process(input) + r.flush(), 1e-6f)
    }

    @Test
    fun `entrada vacia devuelve salida vacia`() {
        assertEquals(0, LinearResampler.resample(FloatArray(0), 44_100, 16_000).size)
    }

    @Test
    fun `una constante sigue siendo constante al bajar de 48k a 16k`() {
        val input = FloatArray(4800) { 0.25f }
        val out = LinearResampler.resample(input, 48_000, 16_000)
        assertTrue("longitud ${out.size}", abs(out.size - 1600) <= 1)
        assertTrue(out.all { abs(it - 0.25f) < 1e-6f })
    }

    @Test
    fun `una rampa se interpola linealmente de 44100 a 16000`() {
        val input = FloatArray(44_100) { it.toFloat() }
        val out = LinearResampler.resample(input, 44_100, 16_000)
        val step = 44_100.0 / 16_000.0
        assertTrue("longitud ${out.size}", abs(out.size - 16_000) <= 1)
        for (k in out.indices step 997) {
            assertEquals((k * step).toFloat(), out[k], 0.01f)
        }
    }

    @Test
    fun `subir de 8k a 16k interpola los puntos intermedios`() {
        val out = LinearResampler.resample(floatArrayOf(0f, 1f, 0f), 8_000, 16_000)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 0.5f, 0f), out, 1e-6f)
    }

    @Test
    fun `procesar por bloques es identico a procesar de una vez`() {
        val rnd = Random(42)
        val input = FloatArray(30_000) { sin(2 * PI * 440 * it / 44_100).toFloat() }
        val whole = LinearResampler.resample(input, 44_100, 16_000)

        val resampler = LinearResampler(44_100, 16_000)
        val pieces = ArrayList<Float>()
        var offset = 0
        while (offset < input.size) {
            val len = minOf(rnd.nextInt(1, 5000), input.size - offset)
            val chunk = input.copyOfRange(offset, offset + len)
            resampler.process(chunk, len).forEach { pieces.add(it) }
            offset += len
        }
        resampler.flush().forEach { pieces.add(it) }
        assertEquals(whole.size, pieces.size)
        for (i in whole.indices) assertEquals(whole[i], pieces[i], 1e-5f)
    }

    @Test
    fun `mezcla estereo a mono promediando canales`() {
        val stereo = shortArrayOf(1000, 3000, -2000, 2000, 32767, 32767)
        val out = FloatArray(3)
        val n = PcmConverter.int16ToMono(stereo, stereo.size, 2, out)
        assertEquals(3, n)
        assertEquals(2000f / 32768f, out[0], 1e-6f)
        assertEquals(0f, out[1], 1e-6f)
        assertEquals(32767f / 32768f, out[2], 1e-6f)
    }

    @Test
    fun `mono int16 se normaliza a -1 1`() {
        val out = FloatArray(2)
        PcmConverter.int16ToMono(shortArrayOf(-32768, 16384), 2, 1, out)
        assertEquals(-1f, out[0], 0f)
        assertEquals(0.5f, out[1], 0f)
    }

    @Test
    fun `mezcla float multicanal`() {
        val out = FloatArray(2)
        val n = PcmConverter.floatToMono(floatArrayOf(1f, 0f, 0f, 0.5f, 0.5f, 0f), 6, 3, out)
        assertEquals(2, n)
        assertEquals(1f / 3f, out[0], 1e-6f)
        assertEquals(1f / 3f, out[1], 1e-6f)
    }
}
