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
    fun `supports itemprop single quotes and unquoted content`() {
        val html = """
            <meta itemprop='og:title' content='Заголовок'>
            <meta property=og:image content=https://cdn.x/u.png>
            <meta property="og:description" content="a &quot;b&quot; &#39;c&#39; &lt;d&gt; &nbsp;e">
        """.trimIndent()

        val preview = OgParser.parse(html, "https://site.ru")!!

        assertEquals("Заголовок", preview.title)
        assertEquals("https://cdn.x/u.png", preview.imageUrl)
        assertEquals("a \"b\" 'c' <d>  e", preview.description)
    }

    @Test
    fun `falls back to twitter image and og image url`() {
        val twitter = OgParser.parse(
            """<meta name="twitter:image" content="https://cdn/t.png">""",
            "https://site.ru"
        )!!
        val ogUrl = OgParser.parse(
            """<meta property="og:image:url" content="https://cdn/o.png">""",
            "https://site.ru"
        )!!

        assertEquals("https://cdn/t.png", twitter.imageUrl)
        assertEquals("https://cdn/o.png", ogUrl.imageUrl)
    }

    @Test
    fun `blank title with image returns preview without title`() {
        val preview = OgParser.parse(
            """<meta property="og:title" content="  "><meta property="og:image" content="/i.png">""",
            "https://site.ru"
        )!!

        assertNull(preview.title)
        assertEquals("https://site.ru/i.png", preview.imageUrl)
    }

    @Test
    fun `ignores meta without content and keeps the first duplicate`() {
        val html = """
            <meta property="og:site_name">
            <meta property="og:title" content="first">
            <meta property="og:title" content="second">
            <meta property="og:image" content="/i.png">
        """.trimIndent()

        val preview = OgParser.parse(html, "https://site.ru")!!

        assertEquals("first", preview.title)
    }

    @Test
    fun `invalid base url does not crash and keeps relative refs`() {
        val preview = OgParser.parse(
            """<meta property="og:title" content="t"><meta property="og:image" content="/i.png">""",
            "not a url"
        )!!

        assertEquals("/i.png", preview.imageUrl)
        assertNull(preview.siteName)
        assertEquals("/i.png", OgParser.resolve("not a url", "/i.png"))
        assertNull(OgParser.host("not a url"))
    }

    @Test
    fun `resolves protocol relative image`() {
        val html = """<meta property="og:image" content="//cdn.x/y.png">"""
        val preview = OgParser.parse(html, "https://site.ru/page")!!

        assertEquals("https://cdn.x/y.png", preview.imageUrl)
    }
}
