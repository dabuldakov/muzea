package com.example.muzea.data.repository

import com.example.muzea.domain.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class GifCacheTest {

    private lateinit var file: File
    private lateinit var cache: GifCache

    private fun gif(id: String) = Gif("klipy", id, null, "u$id", "p$id", 1, 1)

    @Before
    fun setUp() {
        file = File.createTempFile("gif-cache", ".json").apply { delete() }
        cache = GifCache(file)
    }

    @Test
    fun `put and get`() {
        cache.put(GifCache.recentKey(), listOf(gif("a")))

        assertEquals(listOf("a"), cache.getStale(GifCache.recentKey())!!.map { it.providerGifId })
    }

    @Test
    fun `missing key returns null`() {
        assertNull(cache.getStale("nope"))
        assertNull(cache.getFresh("nope", Long.MAX_VALUE))
    }

    @Test
    fun `getFresh honors ttl`() {
        cache.put(GifCache.recentKey(), listOf(gif("a")))

        assertEquals(1, cache.getFresh(GifCache.recentKey(), 60_000)!!.size)
        assertNull(cache.getFresh(GifCache.recentKey(), -1))
    }

    @Test
    fun `different keys are isolated`() {
        cache.put(GifCache.chatKey("chat-1"), listOf(gif("c1")))
        cache.put(GifCache.chatKey("chat-2"), listOf(gif("c2")))

        assertEquals("c1", cache.getStale(GifCache.chatKey("chat-1"))!!.single().providerGifId)
        assertEquals("c2", cache.getStale(GifCache.chatKey("chat-2"))!!.single().providerGifId)
    }

    @Test
    fun `search key is normalized`() {
        assertEquals(GifCache.searchKey("Cat "), GifCache.searchKey("cat"))
    }

    @Test
    fun `prepend adds to front and dedups`() {
        cache.put(GifCache.chatKey("c"), listOf(gif("old")))

        cache.prepend(GifCache.chatKey("c"), gif("new"))
        cache.prepend(GifCache.chatKey("c"), gif("new")) // дубликат

        assertEquals(
            listOf("new", "old"),
            cache.getStale(GifCache.chatKey("c"))!!.map { it.providerGifId }
        )
    }

    @Test
    fun `clear removes everything`() {
        cache.put(GifCache.recentKey(), listOf(gif("a")))
        cache.clear()

        assertNull(cache.getStale(GifCache.recentKey()))
    }

    @Test
    fun `persists across instances`() {
        cache.put(GifCache.libraryKey("cat"), listOf(gif("lib1")))

        val reloaded = GifCache(file)

        assertEquals("lib1", reloaded.getStale(GifCache.libraryKey("cat"))!!.single().providerGifId)
    }
}
