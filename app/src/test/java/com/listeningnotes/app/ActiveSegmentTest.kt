package com.listeningnotes.app

import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.ui.study.findActiveSegmentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSegmentTest {
    private fun seg(id: Long, start: Long, end: Long) = Segment(id, 1, start, end, "t$id", false)

    private val segments = listOf(seg(1, 0, 2_000), seg(2, 2_000, 4_000), seg(3, 10_000, 12_000))

    @Test
    fun `devuelve la frase que contiene la posicion`() {
        assertEquals(1L, findActiveSegmentId(segments, 0))
        assertEquals(1L, findActiveSegmentId(segments, 1_999))
        assertEquals(2L, findActiveSegmentId(segments, 2_000))
        assertEquals(3L, findActiveSegmentId(segments, 11_000))
    }

    @Test
    fun `en un silencio largo no queda ninguna activa`() {
        assertNull(findActiveSegmentId(segments, 7_000))
    }

    @Test
    fun `tolera un pequeno margen tras el final`() {
        assertEquals(2L, findActiveSegmentId(segments, 4_300))
    }

    @Test
    fun `lista vacia o posicion negativa`() {
        assertNull(findActiveSegmentId(emptyList(), 100))
        assertNull(findActiveSegmentId(segments, -1))
    }
}
