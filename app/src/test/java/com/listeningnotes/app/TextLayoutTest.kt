package com.listeningnotes.app

import com.listeningnotes.app.export.ExportSegment
import com.listeningnotes.app.export.buildParagraphs
import com.listeningnotes.app.export.wrapText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {
    // Medida de prueba: 1 unidad por carácter.
    private val measure = { s: String -> s.length.toFloat() }

    @Test
    fun `ajusta por palabras al ancho maximo`() {
        val lines = wrapText("one two three four five", 9f, measure)
        assertEquals(listOf("one two", "three", "four five"), lines)
        assertTrue(lines.all { it.length <= 9 })
    }

    @Test
    fun `texto vacio no produce lineas`() {
        assertEquals(emptyList<String>(), wrapText("   ", 10f, measure))
    }

    @Test
    fun `una palabra mas larga que la linea se trocea`() {
        val lines = wrapText("abcdefghij", 4f, measure)
        assertEquals("abcdefghij", lines.joinToString(""))
        assertTrue(lines.all { it.length <= 4 })
    }

    @Test
    fun `parrafos por silencio largo o por numero de frases`() {
        val segs = listOf(
            ExportSegment(0, 1_000, "One."),
            ExportSegment(1_100, 2_000, "Two."),
            ExportSegment(6_000, 7_000, "Three after silence."),
            ExportSegment(7_100, 8_000, "Four."),
        )
        assertEquals(listOf("One. Two.", "Three after silence. Four."), buildParagraphs(segs))
        val many = (0 until 7).map { ExportSegment(it * 1_000L, it * 1_000L + 900, "S$it.") }
        assertEquals(2, buildParagraphs(many, maxSegments = 6).size)
    }

    @Test
    fun `ignora frases vacias`() {
        assertEquals(listOf("Hi."), buildParagraphs(listOf(ExportSegment(0, 1, "  "), ExportSegment(2, 3, "Hi."))))
    }
}
