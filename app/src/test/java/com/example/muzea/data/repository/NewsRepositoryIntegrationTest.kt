package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Интеграционный тест чтения новостей против MockWebServer.
 * Дублирует live-ответ сервера, где imageUrl приходит абсолютным
 * ("http://host:8085/api/news/image/....jpeg"). Префиксация базового URL
 * поверх такого адреса ломала превью — тест фиксирует правильный контракт.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsRepositoryIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private val repository get() = NewsRepository(IntegrationTestClient.mainApi(server))

    private val liveLikeNewsPage = """
        {"content":[
          {"id":47,"title":"Tomsk","content":"huop",
           "imageUrl":"https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg",
           "author":"dabuldakov","publishedAt":"2026-09-20T04:07:36.573072",
           "relatedVideo":{"id":29,"title":"tomsk","description":null,
             "url":"/api/videos/stream/77b0e3ac-137a-4e47-bee4-011294332842.mp4",
             "thumbnailUrl":"https://api-muzea.su/api/videos/thumbnail/0e8c7c4f-9eb4-463f-b498-39ed65dc00f0.jpeg",
             "fileSize":29683941,"duration":null,"views":4,"likes":0,
             "uploadedBy":"dabuldakov","uploadedAt":"2026-09-20T04:05:25.802792"}}
        ],
        "empty":false,"first":true,"last":false,"number":0,"numberOfElements":1,
        "size":20,"totalElements":1,"totalPages":1}
    """.trimIndent()

    @Test
    fun `getNews parses absolute image url and keeps it unmodified`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeNewsPage)
        )

        val result = repository.getNews(page = 0, size = 20).toList().last()

        assertTrue(result is NetworkResult.Success)
        val news = result.data!!.single()
        assertEquals(
            "https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg",
            news.imageUrl
        )
        assertNotNull(news.relatedVideo)
        // Адаптер строит URL через MediaUrl — он не должен добавлять базовый префикс.
        assertEquals(news.imageUrl, MediaUrl.main(news.imageUrl))

        val request = server.takeRequest()
        assertEquals("/api/news?page=0&size=20", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `getNewsById parses single news`() = runTest {
        val single = """
            {"id":47,"title":"Tomsk","content":"huop",
             "imageUrl":"https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg",
             "author":"dabuldakov","publishedAt":"2026-09-20T04:07:36.573072","relatedVideo":null}
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(single)
        )

        val result = repository.getNewsById(47).toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(47L, result.data!!.id)
        assertEquals(
            "https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg",
            result.data!!.imageUrl
        )

        val request = server.takeRequest()
        assertEquals("/api/news/47", request.path)
    }

    @Test
    fun `getNews surfaces server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getNews().toList().last()

        assertTrue(result is NetworkResult.Error)
    }
}