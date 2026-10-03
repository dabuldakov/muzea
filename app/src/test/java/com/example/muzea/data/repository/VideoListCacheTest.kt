package com.example.muzea.data.repository

import com.example.muzea.data.model.VideoResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoListCacheTest {

    private fun video(id: Long) = VideoResponse(
        id = id,
        title = "Title $id",
        description = null,
        url = "https://example.com/video$id",
        thumbnailUrl = null,
        fileSize = null,
        duration = null,
        views = 0,
        likes = null,
        uploadedBy = "me",
        uploadedAt = "2026-01-01T00:00:00"
    )

    @Before
    fun setUp() = VideoListCache.clear()

    @After
    fun tearDown() = VideoListCache.clear()

    @Test
    fun `empty cache reports no videos`() {
        assertFalse(VideoListCache.has())
        assertTrue(VideoListCache.get().isEmpty())
    }

    @Test
    fun `put makes videos available in order`() {
        VideoListCache.put(listOf(video(3), video(1), video(2)))

        assertTrue(VideoListCache.has())
        assertEquals(listOf(3L, 1L, 2L), VideoListCache.get().map { it.id })
    }

    @Test
    fun `put replaces previous contents`() {
        VideoListCache.put(listOf(video(1), video(2)))
        VideoListCache.put(listOf(video(9)))

        assertEquals(listOf(9L), VideoListCache.get().map { it.id })
    }

    @Test
    fun `cache is capped and evicts the oldest`() {
        VideoListCache.put((1..250).map { video(it.toLong()) })

        val stored = VideoListCache.get()
        assertEquals(200, stored.size)
        assertEquals(51L, stored.first().id)
        assertEquals(250L, stored.last().id)
    }

    @Test
    fun `clear empties the cache`() {
        VideoListCache.put(listOf(video(1)))

        VideoListCache.clear()

        assertFalse(VideoListCache.has())
        assertTrue(VideoListCache.get().isEmpty())
    }
}
