package com.listeningnotes.app

import com.listeningnotes.app.export.ExportSegment
import com.listeningnotes.app.export.SegmentExporter
import com.listeningnotes.app.util.formatClock
import com.listeningnotes.app.whisper.ModelDownloader
import com.listeningnotes.app.whisper.SegmentCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentExporterTest {

    private val segments = listOf(
        ExportSegment(0, 2_500, "Hello there."),
        ExportSegment(2_500, 5_000, "  How   are\nyou?  "),
        ExportSegment(65_000, 70_000, "   "),
        ExportSegment(3_725_000, 3_730_000, "One hour later."),
    )

    @Test
    fun `texto con marcas de tiempo y titulo`() {
        val text = SegmentExporter.toPlainText("Mi audio", segments)
        assertEquals(
            "Mi audio\n\n[00:00] Hello there.\n[00:02] How are you?\n[1:02:05] One hour later.\n",
            text,
        )
    }

    @Test
    fun `texto sin marcas ni titulo`() {
        val text = SegmentExporter.toPlainText(null, segments, includeTimestamps = false)
        assertEquals("Hello there.\nHow are you?\nOne hour later.\n", text)
    }

    @Test
    fun `lista vacia produce cadena vacia`() {
        assertEquals("", SegmentExporter.toPlainText(null, emptyList()))
        assertEquals("T\n\n", SegmentExporter.toPlainText("T", emptyList()))
    }

    @Test
    fun `nombre de fichero seguro`() {
        assertEquals("a_b_c.txt", SegmentExporter.suggestedFileName("a/b:c"))
        assertEquals("transcripcion.txt", SegmentExporter.suggestedFileName("   "))
    }

    @Test
    fun `formato de reloj`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("01:05", formatClock(65_999))
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("00:00", formatClock(-5))
    }

    @Test
    fun `limpieza de segmentos de whisper`() {
        assertEquals("Hello world", SegmentCleaner.clean("  Hello \n world "))
        assertNull(SegmentCleaner.clean(" [BLANK_AUDIO] "))
        assertNull(SegmentCleaner.clean("   "))
    }

    @Test
    fun `total de Content-Range`() {
        assertEquals(1000L, ModelDownloader.parseContentRangeTotal("bytes 100-999/1000"))
        assertEquals(1000L, ModelDownloader.parseContentRangeTotal("bytes */1000"))
        assertNull(ModelDownloader.parseContentRangeTotal(null))
        assertNull(ModelDownloader.parseContentRangeTotal("bytes 0-1/*"))
    }
}
