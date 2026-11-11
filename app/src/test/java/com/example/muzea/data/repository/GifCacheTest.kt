package com.example.muzea.data.repository

import com.example.muzea.domain.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GifCacheTest {

    private fun gif(url: String, key: String? = null) = Gif(
        provider = "klipy",
        providerGifId = key ?: url,
        title = "g",
        url = url,
        previewUrl = null,
        width = 1,
        height = 1,
        gifKey = key
    )

    private fun cacheFile() = File.createTempFile("gif-cache", ".json").apply {
        delete()
        deleteOnExit()
    }

    @Test
    fun `fresh entries are returned and expired ones are not`() {
        val cache = GifCache(cacheFile())
        cache.put("chat:1", listOf(gif("https://c/1.gif")))

        assertNotNull(cache.getFresh("chat:1", ttlMs = 60_000))
        assertNull(cache.getFresh("chat:1", ttlMs = -1))
        assertNull(cache.getFresh("missing", ttlMs = 60_000))
    }

    @Test
    fun `stale entries survive regardless of age`() {
        val cache = GifCache(cacheFile())
        cache.put("recent", listOf(gif("https://c/1.gif")))

        assertNotNull(cache.getStale("recent"))
        assertNull(cache.getStale("absent"))
    }

    @Test
    fun `legacy cache file is parsed into gif objects`() {
        // Так файл выглядел раньше: Gson писал Entry как обычный объект.
        val file = cacheFile()
        file.writeText(
            """{"chat:1":{"at":123,"items":[{"provider":"klipy","providerGifId":"p1",""" +
                """"title":"g","url":"https://c/1.gif","previewUrl":null,""" +
                """"width":1,"height":1,"mp4Url":null,"gifKey":null}]}}"""
        )

        val items = GifCache(file).getStale("chat:1")

        assertEquals(1, items?.size)
        assertEquals("https://c/1.gif", items?.first()?.url)
    }

    @Test
    fun `broken entry does not break the rest of the cache`() {
        val file = cacheFile()
        file.writeText(
            """{"bad":{"at":"oops","items":[]},"recent":{"at":1,"items":[]}}"""
        )

        val cache = GifCache(file)

        assertNull(cache.getStale("bad"))
        assertNotNull(cache.getStale("recent"))
    }

    @Test
    fun `written cache survives reload with all fields`() {
        val file = cacheFile()
        GifCache(file).put("recent", listOf(gif("https://c/1.gif", key = "k1")))

        val reloaded = GifCache(file).getStale("recent")

        assertEquals("k1", reloaded?.single()?.gifKey)
        assertEquals("klipy", reloaded?.single()?.provider)
    }

    @Test
    fun `prepend deduplicates by gif key and url`() {
        val cache = GifCache(cacheFile())
        cache.prepend("recent", gif("https://c/1.gif", key = "k1"))
        // Тот же ключ — дубликат.
        cache.prepend("recent", gif("https://c/other.gif", key = "k1"))
        // Тот же url — тоже дубликат.
        cache.prepend("recent", gif("https://c/1.gif", key = "k2"))

        assertEquals(1, cache.getStale("recent")!!.size)

        cache.prepend("recent", gif("https://c/2.gif", key = "k3"))
        assertEquals(2, cache.getStale("recent")!!.size)
    }

    @Test
    fun `prepend evicts the oldest entries past the limit`() {
        val cache = GifCache(cacheFile())
        repeat(105) { i ->
            cache.prepend("key-$i", gif("https://c/$i.gif", key = "k$i"))
        }

        // Лимит — 100 записей: самые старые ключи вытеснены.
        assertNull(cache.getStale("key-0"))
        assertNotNull(cache.getStale("key-104"))
    }

    @Test
    fun `clear and remove drop entries`() {
        val cache = GifCache(cacheFile())
        cache.put("chat:1", listOf(gif("https://c/1.gif")))
        cache.put("recent", listOf(gif("https://c/2.gif")))

        cache.remove("chat:1")
        assertNull(cache.getStale("chat:1"))
        assertNotNull(cache.getStale("recent"))

        cache.remove("absent")
        cache.clear()
        assertTrue(cache.getStale("recent").isNullOrEmpty())
    }

    @Test
    fun `entries are persisted and reloaded from disk`() {
        val file = cacheFile()
        GifCache(file).put("recent", listOf(gif("https://c/1.gif")))
        assertTrue(file.exists())

        val reloaded = GifCache(file)

        assertEquals(1, reloaded.getStale("recent")!!.size)
    }

    @Test
    fun `a corrupt cache file is ignored`() {
        val file = cacheFile()
        file.writeText("not-json")

        val cache = GifCache(file)

        assertNull(cache.getStale("recent"))
        // Запись после ошибки не должна падать.
        cache.put("recent", listOf(gif("https://c/2.gif")))
        assertEquals(1, cache.getStale("recent")!!.size)
    }

    @Test
    fun `search key trims and lowercases the query`() {
        assertEquals("search:cat dog", GifCache.searchKey("  Cat Dog "))
    }

    @Test
    fun `entries beyond the maximum are evicted`() {
        val cache = GifCache(cacheFile())
        repeat(101) { i ->
            cache.put("key-$i", listOf(gif("https://c/$i.gif")))
        }

        assertNull(cache.getStale("key-0"))
        assertNotNull(cache.getStale("key-100"))
    }
}
