package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLinksTest {

    @Test
    fun `finds https and www links`() {
        val text = "Смотри https://muzea.su/news и www.example.com/page"
        val urls = MessageLinks.matches(text).map { it.url }

        assertEquals(listOf("https://muzea.su/news", "www.example.com/page"), urls)
    }

    @Test
    fun `trims trailing punctuation`() {
        val urls = MessageLinks.matches("Глянь https://a.b/c, потом (https://d.e/f).")
            .map { it.url }

        assertEquals(listOf("https://a.b/c", "https://d.e/f"), urls)
    }

    @Test
    fun `no links returns empty`() {
        assertTrue(MessageLinks.matches("привет, как дела?").isEmpty())
        assertTrue(MessageLinks.matches(null).isEmpty())
    }

    @Test
    fun `spans cover the trimmed url`() {
        val text = "see https://x.y/page. ok"
        val match = MessageLinks.matches(text).single()

        assertEquals("https://x.y/page", text.substring(match.start, match.end))
    }

    @Test
    fun `normalize adds scheme for www and keeps http`() {
        assertEquals("https://www.example.com", MessageLinks.normalize("www.example.com"))
        assertEquals("http://example.com", MessageLinks.normalize("http://example.com"))
        assertEquals("https://example.com", MessageLinks.normalize("https://example.com"))
    }
}
