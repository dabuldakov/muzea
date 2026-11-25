package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.MediaUrl
import com.example.muzea.core.Resource
import com.example.muzea.core.firstTerminal
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

    private val repository get() = NewsRepositoryImpl(IntegrationTestClient.mainApi(server))

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

        assertTrue(result is Resource.Success)
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

        assertTrue(result is Resource.Success)
        assertEquals(47L, result.data!!.id)
        assertEquals(
            "https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg",
            result.data!!.imageUrl
        )

        val request = server.takeRequest()
        assertEquals("/api/news/47", request.path)
    }

    @Test
    fun `comments parse the cursor page`() = runTest {
        val body = """
            {"comments":[{"id":12,"newsId":47,"content":"Привет","author":"bob",
             "createdAt":"2026-11-25T10:00:00"}],
             "total":2,"hasMore":true,"nextBefore":12}
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val result = repository.getComments(47, before = null, size = 20)
            .firstTerminal()

        assertTrue(result is Resource.Success)
        val page = result.data!!
        assertEquals(1, page.comments.size)
        assertEquals("Привет", page.comments.single().content)
        assertEquals(2L, page.total)
        assertTrue(page.hasMore)
        assertEquals(12L, page.nextBefore)

        val request = server.takeRequest()
        assertEquals("/api/news/47/comments?size=20", request.path)
    }

    @Test
    fun `like response returns the server counter`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"liked":true,"likes":7}""")
        )

        val result = repository.likeNews(47).firstTerminal()

        assertTrue(result is Resource.Success)
        assertEquals(7L, result.data)
        assertEquals("POST", server.takeRequest().method)
    }


    @Test
    fun `unlike and liked ids use the expected endpoints`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"liked":false,"likes":1}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"likedIds":[47]}""")
        )

        assertEquals(1L, (repository.unlikeNews(47).firstTerminal() as Resource.Success).data)
        assertEquals(setOf(47L), (repository.getLikedNewsIds().firstTerminal() as Resource.Success).data)

        val unlikeRequest = server.takeRequest()
        val likedRequest = server.takeRequest()
        assertEquals("/api/news/47/like", unlikeRequest.path)
        assertEquals("DELETE", unlikeRequest.method)
        assertEquals("/api/news/likes/me", likedRequest.path)
    }

    @Test
    fun `registerView posts to the view endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        repository.registerView(47)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/news/47/view", request.path)
    }

    @Test
    fun `updateNews sends multipart and parses the result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(singleNewsJson(title = "Updated"))
        )

        val result = repository.updateNews(
            id = 47,
            title = "Updated",
            content = "Text",
            videoId = null,
            removeVideo = false,
            imageFile = null,
            removeImage = true
        ).firstTerminal()

        assertTrue(result is Resource.Success)
        assertEquals("Updated", result.data!!.title)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/news/47", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"title\""))
        assertTrue(body.contains("removeImage"))
    }

    @Test
    fun `add and delete comment use the expected contract`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":12,"newsId":47,"content":"Hi","author":"vovan",
                       "createdAt":"2026-11-25T10:00:00"}"""
                )
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val added = repository.addComment(47, "Hi").firstTerminal()
        assertEquals("Hi", (added as Resource.Success).data!!.content)
        val addRequest = server.takeRequest()
        assertEquals("POST", addRequest.method)
        assertEquals("/api/news/47/comments", addRequest.path)
        assertTrue(addRequest.body.readUtf8().contains("\"content\":\"Hi\""))

        assertTrue(repository.deleteComment(47, 12).firstTerminal() is Resource.Success)
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/api/news/47/comments/12", deleteRequest.path)
    }

    @Test
    fun `reportComment sends NEWS_COMMENT to reports`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        assertTrue(repository.reportComment(12, "ABUSE"))

        val request = server.takeRequest()
        assertEquals("/api/reports", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("NEWS_COMMENT"))
        assertTrue(body.contains("\"contentId\":12"))
    }

    private fun singleNewsJson(title: String) = """
        {"id":47,"title":"$title","content":"Text","imageUrl":null,
         "author":"vovan","publishedAt":"2026-11-25T10:00:00","relatedVideo":null,
         "views":1,"likes":2,"commentsCount":3,"likedByMe":false}
    """.trimIndent()

    @Test
    fun `getNews surfaces server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getNews().toList().last()

        assertTrue(result is Resource.Error)
    }

    @Test
    fun `terminal collection does not violate flow transparency`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeNewsPage)
        )

        // firstTerminal() обрывает поток на первом Success/Error (AbortFlowException).
        // Прежняя реализация ловила его как Exception и эмитила Resource.Error
        // из catch-блока, что роняло приложение с IllegalStateException:
        // "Flow exception transparency is violated".
        val result = repository.getNews(page = 0, size = 20).firstTerminal()

        assertTrue(result is Resource.Success)
    }
}