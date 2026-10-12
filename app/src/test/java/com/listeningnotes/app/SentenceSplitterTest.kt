package com.listeningnotes.app

import com.listeningnotes.app.util.normalizeWord
import com.listeningnotes.app.util.splitTokens
import com.listeningnotes.app.whisper.RawSegment
import com.listeningnotes.app.whisper.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {
    private fun seg(text: String, start: Long = 1_000, end: Long = 11_000) = RawSegment(start, end, text)

    @Test
    fun `una sola frase no se toca`() {
        val out = SentenceSplitter.split(seg("Hello there, how are you doing today?"))
        assertEquals(listOf(seg("Hello there, how are you doing today?")), out)
    }

    @Test
    fun `divide varias frases y reparte el tiempo sin huecos`() {
        val out = SentenceSplitter.split(seg("I went to the store yesterday. It was closed! Why would they do that?"))
        assertEquals(3, out.size)
        assertEquals("I went to the store yesterday.", out[0].text)
        assertEquals("It was closed!", out[1].text)
        assertEquals("Why would they do that?", out[2].text)
        assertEquals(1_000L, out.first().startMs)
        assertEquals(11_000L, out.last().endMs)
        for (i in 1 until out.size) assertEquals(out[i - 1].endMs, out[i].startMs)
        assertTrue(out.all { it.endMs > it.startMs })
    }

    @Test
    fun `no corta en abreviaturas iniciales ni decimales`() {
        assertEquals(1, SentenceSplitter.split(seg("Mr. Smith paid 3.5 dollars to J. Brown at 5 p.m. today.")).size)
        assertEquals(1, SentenceSplitter.split(seg("We sell pens, paper, etc. and more things here.")).size)
    }

    @Test
    fun `une frases demasiado cortas con la vecina`() {
        val out = SentenceSplitter.split(seg("Yes. I know the answer to your question."))
        assertEquals(1, out.size)
        assertEquals("Yes. I know the answer to your question.", out[0].text)
    }

    @Test
    fun `parte una frase larguisima por la coma central`() {
        val text = "When we finally arrived at the old house after driving for hours through the rain, " +
            "we discovered that nobody had remembered to bring the keys to open the front door."
        val out = SentenceSplitter.split(seg(text))
        assertEquals(2, out.size)
        assertTrue(out[0].text.endsWith("rain,"))
    }

    @Test
    fun `palabras y normalizacion`() {
        assertEquals(listOf("Don't", "stop,", "\"believing\"."), splitTokens("  Don't stop, \"believing\".  "))
        assertEquals("believing", normalizeWord("\"Believing\"."))
        assertEquals("don't", normalizeWord("Don’t"))
        assertEquals("", normalizeWord("..."))
    }
}
