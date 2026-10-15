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

    @Test
    fun `merge combines same groups in release order`() {
        val old = "## ✨ Новое\n- первое\n## 🐛 Исправления\n- баг старый"
        val new = "## ✨ Новое\n- второе\n## 🐛 Исправления\n- баг новый"

        val merged = mergeChangelog(listOf(old, new))

        assertEquals(2, merged.size)
        assertEquals("✨ Новое", merged[0].title)
        assertEquals(listOf("первое", "второе"), merged[0].items)
        assertEquals(listOf("баг старый", "баг новый"), merged[1].items)
    }

    @Test
    fun `merge keeps distinct groups and ignores blanks`() {
        val a = "## ✨ Новое\n- a"
        val b = "## 🔧 CI\n- b"

        val merged = mergeChangelog(listOf(a, null, b))

        assertEquals(listOf("✨ Новое", "🔧 CI"), merged.map { it.title })
        assertEquals(listOf("a"), merged[0].items)
        assertEquals(listOf("b"), merged[1].items)
    }
}
