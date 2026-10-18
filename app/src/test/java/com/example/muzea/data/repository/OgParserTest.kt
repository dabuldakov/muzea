package com.example.muzea.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OgParserTest {

    @Test
    fun `parses open graph tags and decodes entities`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Заголовок &amp; ещё">
            <meta property="og:image" content="/img/cover.png">
            <meta property="og:description" content="Описание страницы">
            <meta property="og:site_name" content="Muzea">
            </head></html>
        """.trimIndent()

        val preview = OgParser.parse(html, "https://muzea.su/news/1")!!

        assertEquals("Заголовок & ещё", preview.title)
        assertEquals("https://muzea.su/img/cover.png", preview.imageUrl)
        assertEquals("Описание страницы", preview.description)
        assertEquals("Muzea", preview.siteName)
    }

    @Test
    fun `falls back to title tag and handles reversed attribute order`() {
        val html = """
            <head>
            <meta content="https://cdn.example.com/pic.jpg" property="og:image"/>
            <title>Просто заголовок</title>
            </head>
        """.trimIndent()

        val preview = OgParser.parse(html, "https://example.com/a")!!

        assertEquals("Просто заголовок", preview.title)
        assertEquals("https://cdn.example.com/pic.jpg", preview.imageUrl)
        // site_name нет — берём host
        assertEquals("example.com", preview.siteName)
    }

    @Test
    fun `matches name attribute too`() {
        val html = """<meta name="description" content="desc"><meta name="twitter:title" content="tw">"""
        val preview = OgParser.parse(html, "https://a.b")!!

        assertEquals("tw", preview.title)
        assertEquals("desc", preview.description)
    }

    @Test
    fun `returns null when nothing useful`() {
        assertNull(OgParser.parse("<html><body>no meta here</body></html>", "https://a.b"))
    }

    @Test
    fun `resolves protocol relative image`() {
        val html = """<meta property="og:image" content="//cdn.x/y.png">"""
        val preview = OgParser.parse(html, "https://site.ru/page")!!

        assertEquals("https://cdn.x/y.png", preview.imageUrl)
    }
}
