package com.example.muzea.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkPreviewStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var store: LinkPreviewStore

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = LinkPreviewStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loads Open Graph fields from html`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(
                    """
                    <html><head>
                      <meta property="og:title" content="Большая статья">
                      <meta property="og:description" content="О чём-то важном">
                      <meta property="og:image" content="/cover.jpg">
                      <meta property="og:site_name" content="Example">
                    </head></html>
                    """.trimIndent()
                )
        )
        val url = server.url("/page").toString()

        val preview = store.load(url)

        assertEquals("Большая статья", preview?.title)
        assertEquals("О чём-то важном", preview?.description)
        assertEquals(server.url("/cover.jpg").toString(), preview?.imageUrl)
        assertEquals("Example", preview?.siteName)
        assertTrue(store.isLoaded(url))
        assertEquals(preview, store.cached(url))
    }

    @Test
    fun `second load is served from the in-memory cache`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""<meta property="og:title" content="Once">""")
        )
        val url = server.url("/page").toString()

        store.load(url)
        store.load(url)

        assertEquals(1, server.requestCount)
        assertFalse(store.cached("https://unknown.example").let { it != null })
    }

    @Test
    fun `non-html responses are not parsed`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"no"}""")
        )
        val url = server.url("/data").toString()

        assertNull(store.load(url))
        assertTrue(store.isLoaded(url))
    }

    @Test
    fun `http errors are cached as failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/broken").toString()

        assertNull(store.load(url))
        assertTrue(store.isLoaded(url))
    }

    @Test
    fun `invalid urls are rejected without a request`() = runTest {
        assertNull(store.load("not a url"))
        assertNull(store.load("ftp://example.com/file"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `page without metadata is not a preview`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>nothing</body></html>")
        )

        assertNull(store.load(server.url("/plain").toString()))
    }

    @Test
    fun `cached and isLoaded are false before the first load`() {
        assertNull(store.cached("https://example.com"))
        assertFalse(store.isLoaded("https://example.com"))
    }
}
