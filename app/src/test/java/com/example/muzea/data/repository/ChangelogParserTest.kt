package com.example.muzea.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun `parses groups with items`() {
        val body = """
            ## ✨ Новое
            - Chat: кликабельные ссылки
            - Share: приём ссылок

            ## 🐛 Исправления
            - Chat: видимый текст
        """.trimIndent()

        val groups = parseChangelog(body)

        assertEquals(2, groups.size)
        assertEquals("✨ Новое", groups[0].title)
        assertEquals(listOf("Chat: кликабельные ссылки", "Share: приём ссылок"), groups[0].items)
        assertEquals("🐛 Исправления", groups[1].title)
        assertEquals(listOf("Chat: видимый текст"), groups[1].items)
    }

    @Test
    fun `supports asterisk bullets`() {
        val groups = parseChangelog("## Group\n* one\n* two")

        assertEquals(1, groups.size)
        assertEquals(listOf("one", "two"), groups[0].items)
    }

    @Test
    fun `ignores lines without markers`() {
        val body = """
            ## Group
            - real item
            Full Changelog: v1.0.0...v1.1.0
            Some paragraph
        """.trimIndent()

        val groups = parseChangelog(body)

        assertEquals(1, groups.size)
        assertEquals(listOf("real item"), groups[0].items)
    }

    @Test
    fun `drops groups without items`() {
        val groups = parseChangelog("## Empty\n\n## With\n- item")

        assertEquals(1, groups.size)
        assertEquals("With", groups[0].title)
    }

    @Test
    fun `blank body yields no groups`() {
        assertTrue(parseChangelog(null).isEmpty())
        assertTrue(parseChangelog("").isEmpty())
        assertTrue(parseChangelog("   ").isEmpty())
    }
}
