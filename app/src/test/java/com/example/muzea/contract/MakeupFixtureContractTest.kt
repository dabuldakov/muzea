package com.example.muzea.contract

import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.model.PageResponse
import com.example.muzea.data.model.UserResponse
import com.example.muzea.data.model.VideoResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Общие makeup-фикстуры с iOS: news/video/user. */
class MakeupFixtureContractTest {

    private val gson = Gson()

    private fun text(path: String): String =
        javaClass.getResourceAsStream("/fixtures/makeup/$path")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Fixture not found: $path")

    @Test
    fun `news page maps to domain with related video`() {
        val type = object : TypeToken<PageResponse<NewsResponse>>() {}.type
        val page: PageResponse<NewsResponse> = gson.fromJson(text("news_page.json"), type)
        val news = page.content.map { it.toDomain() }

        assertEquals(1, news.size)
        assertEquals(54L, news[0].id)
        assertEquals("dabuldakov", news[0].author)
        assertEquals(18L, news[0].relatedVideo?.id)
        assertTrue(
            news[0].relatedVideo!!
                .getFullVideoUrl("https://api-muzea.su")
                .endsWith("/api/videos/stream/video.mp4")
        )
    }

    @Test
    fun `news without video keeps masked author`() {
        val news: NewsResponse = gson.fromJson(text("news.json"), NewsResponse::class.java)
        val domain = news.toDomain()

        assertNull(domain.relatedVideo)
        assertNull(domain.imageUrl)
        assertEquals("Удалённый пользователь", domain.author)
    }

    @Test
    fun `videos map and resolve urls`() {
        val type = object : TypeToken<List<VideoResponse>>() {}.type
        val videos: List<VideoResponse> = gson.fromJson(text("videos.json"), type)
        val domain = videos.map { it.toDomain() }

        assertEquals(1, domain.size)
        assertEquals(18L, domain[0].id)
        assertEquals(3, domain[0].likes)
        assertEquals(12.5, domain[0].durationSeconds!!, 0.001)
        assertTrue(
            domain[0].getFullVideoUrl("https://api-muzea.su")
                .endsWith("/api/videos/stream/video.mp4")
        )
        assertTrue(
            domain[0].getFullThumbnailUrl("https://api-muzea.su")
                ?.endsWith("/api/videos/thumbnail/video.jpeg") == true
        )
    }

    @Test
    fun `user maps without email`() {
        val user: UserResponse = gson.fromJson(text("user.json"), UserResponse::class.java)
        val domain = user.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("dabuldakov", domain.userName)
        assertTrue(domain.enabled)
    }
}
