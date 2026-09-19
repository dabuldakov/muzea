package com.example.muzea.ui.news

import com.example.muzea.data.model.NewsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedFilterTest {

    private fun news(id: Long, author: String) = NewsResponse(
        id = id,
        title = "Title $id",
        content = "Content $id",
        imageUrl = null,
        relatedVideo = null,
        author = author,
        publishedAt = "2026-01-01T00:00:00"
    )

    private val feed = listOf(
        news(1, "dabuldakov"),
        news(2, "maria"),
        news(3, "stranger"),
        news(4, "vovan"),
        news(5, " DABULDAKOV ")
    )

    @Test
    fun `keeps news from contacts and self`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = setOf("dabuldakov"),
            ownUsername = "vovan"
        )

        assertEquals(listOf(1L, 4L), result.map { it.id })
    }

    @Test
    fun `trims whitespace from contact usernames`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = setOf("  dabuldakov  "),
            ownUsername = null
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `matching is case-sensitive`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = setOf("dabuldakov"),
            ownUsername = null
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `shows only own news when contacts are empty`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = emptySet(),
            ownUsername = "vovan"
        )

        assertEquals(listOf(4L), result.map { it.id })
    }

    @Test
    fun `returns empty when no contacts and no own username`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = emptySet(),
            ownUsername = null
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `trims own username before matching`() {
        val result = NewsFeedFilter.filterByContacts(
            news = feed,
            contactUsernames = emptySet(),
            ownUsername = "  vovan  "
        )

        assertEquals(listOf(4L), result.map { it.id })
    }
}