package com.example.muzea.ui.video

import com.example.muzea.data.model.VideoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFeedFilterTest {

    private fun video(id: Long, uploadedBy: String) = VideoResponse(
        id = id,
        title = "Title $id",
        description = null,
        url = "https://example.com/video$id",
        thumbnailUrl = null,
        fileSize = null,
        duration = null,
        views = 0,
        likes = null,
        uploadedBy = uploadedBy,
        uploadedAt = "2026-01-01T00:00:00"
    )

    private val feed = listOf(
        video(1, "dabuldakov"),
        video(2, "maria"),
        video(3, "stranger"),
        video(4, "vovan"),
        video(5, "VOVAN"),
        video(6, "  vovan  ")
    )

    @Test
    fun `keeps only own videos`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = "vovan"
        )

        assertEquals(listOf(4L, 6L), result.map { it.id })
    }

    @Test
    fun `matching is case-sensitive`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = "VOVAN"
        )

        assertEquals(listOf(5L), result.map { it.id })
    }

    @Test
    fun `trims own username before matching`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = "  vovan  "
        )

        assertEquals(listOf(4L, 6L), result.map { it.id })
    }

    @Test
    fun `returns empty when own username is null`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = null
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty when own username is blank`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = "   "
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty when nobody matches`() {
        val result = VideoFeedFilter.filterOwn(
            videos = feed,
            ownUsername = "nobody"
        )

        assertTrue(result.isEmpty())
    }
}
