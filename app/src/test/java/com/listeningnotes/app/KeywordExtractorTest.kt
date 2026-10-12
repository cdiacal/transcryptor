package com.listeningnotes.app

import com.listeningnotes.app.analysis.KeywordExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordExtractorTest {
    private val texts = listOf(
        "Hello everyone, and welcome back to the show.",
        "Today we are talking about travelling on a small budget.",
        "My guest has visited more than forty countries.",
        "I went to Portugal with a backpack and almost no money.",
        "Last year I travelled to Lisbon. Travel changed my life, and a backpack is all you need.",
        "Budget travel is about choosing cheap hostels and local food.",
        "John's hostel was cheap, and the hostels in Lisbon are great.",
    )

    private val keywords = KeywordExtractor.extract(texts)
    private val words = keywords.map { it.word }

    @Test
    fun `incluye el vocabulario del tema`() {
        assertTrue("backpack" in words)
        assertTrue("budget" in words)
        assertTrue(words.any { it.startsWith("travel") })
        assertTrue(words.any { it.startsWith("hostel") })
    }

    @Test
    fun `descarta palabras funcionales basicas y nombres propios`() {
        for (w in listOf("the", "and", "about", "with", "have", "time", "money", "went", "this")) {
            assertFalse("no debería incluir $w", w in words)
        }
        assertFalse("portugal" in words)
        assertFalse("lisbon" in words)
    }

    @Test
    fun `agrupa formas de una misma palabra`() {
        assertEquals(1, words.count { it.startsWith("travel") })
        assertEquals(1, words.count { it.startsWith("hostel") })
        val travel = keywords.first { it.word.startsWith("travel") }
        assertEquals(4, travel.count)
    }

    @Test
    fun `las repetidas puntuan mas y definen el tema`() {
        val topic = KeywordExtractor.topic(keywords)
        assertTrue(topic.size in 2..4)
        assertTrue(topic.any { it.startsWith("travel") })
        assertTrue(topic.any { it == "backpack" || it == "budget" || it.startsWith("hostel") })
    }

    @Test
    fun `guarda la primera frase donde aparece y respeta el limite`() {
        assertEquals(3, keywords.first { it.word == "backpack" }.segmentIndex)
        assertTrue(KeywordExtractor.extract(texts, limit = 3).size == 3)
        assertEquals(emptyList<Any>(), KeywordExtractor.extract(emptyList()))
    }

    @Test
    fun `agrupacion de claves`() {
        assertEquals(KeywordExtractor.stemKey("travelling"), KeywordExtractor.stemKey("travelled"))
        assertEquals(KeywordExtractor.stemKey("travel"), KeywordExtractor.stemKey("travels"))
        assertEquals(KeywordExtractor.stemKey("study"), KeywordExtractor.stemKey("studies"))
    }
}
