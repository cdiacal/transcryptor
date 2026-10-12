package com.listeningnotes.app

import com.listeningnotes.app.util.likePattern
import org.junit.Assert.assertEquals
import org.junit.Test

class SqlLikeTest {
    @Test
    fun `texto normal se rodea de comodines`() {
        assertEquals("%hello%", likePattern("hello"))
    }

    @Test
    fun `se escapan los comodines del usuario`() {
        assertEquals("%100\\%%", likePattern("100%"))
        assertEquals("%a\\_b%", likePattern("a_b"))
        assertEquals("%c\\\\d%", likePattern("c\\d"))
    }
}
