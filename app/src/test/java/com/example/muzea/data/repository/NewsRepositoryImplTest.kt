package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.LikedNewsResponse
import com.example.muzea.data.model.NewsCommentResponse
import com.example.muzea.data.model.NewsCommentsResponse
import com.example.muzea.data.model.NewsCreateResponse
import com.example.muzea.data.model.NewsLikeResponse
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.model.PageResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class NewsRepositoryImplTest {

    private val api = mockk<ApiService>()
    private val repository = NewsRepositoryImpl(api)

    @Test
    fun `getNews maps the page content`() = runTest {
        coEvery { api.getNews(0, 20) } returns httpOk(page(news(1), news(2)))

        val last = repository.getNews().toList().last()

        assertEquals(listOf(1L, 2L), (last as Resource.Success).data!!.map { it.id })
    }

    @Test
    fun `getNews reports a failure`() = runTest {
        coEvery { api.getNews(0, 20) } returns httpError(500)

        val last = repository.getNews().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load news"))
    }

    @Test
    fun `getNewsById maps a single item`() = runTest {
        coEvery { api.getNewsById(1L) } returns httpOk(news(1, title = "Заголовок"))

        val last = repository.getNewsById(1L).toList().last()

        assertEquals("Заголовок", (last as Resource.Success).data!!.title)
    }

    @Test
    fun `getNewsById reports a network error`() = runTest {
        coEvery { api.getNewsById(1L) } throws IOException("offline")

        val last = repository.getNewsById(1L).toList().last()

        assertEquals("Network error: offline", (last as Resource.Error).message)
    }

    @Test
    fun `createNews returns the new id`() = runTest {
        coEvery { api.createNews(any(), any(), null, null) } returns httpOk(NewsCreateResponse(42))

        val last = repository.createNews("Title", "Text", null, null).toList().last()

        assertEquals(42L, (last as Resource.Success).data)
    }

    @Test
    fun `createNews attaches an image part when a file is given`() = runTest {
        val image = File.createTempFile("news", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        coEvery { api.createNews(any(), any(), 5L, any()) } returns httpOk(NewsCreateResponse(43))

        val last = repository.createNews("Title", "Text", 5L, image).toList().last()

        assertEquals(43L, (last as Resource.Success).data)
        coVerify(exactly = 1) { api.createNews(any(), any(), 5L, any()) }
    }

    @Test
    fun `createNews reports a failure`() = runTest {
        coEvery { api.createNews(any(), any(), any(), any()) } returns httpError(400)

        val last = repository.createNews("Title", "Text", null, null).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to create news"))
    }

    @Test
    fun `deleteNews explains the forbidden case`() = runTest {
        coEvery { api.deleteNews(1L) } returns httpError(403)

        val last = repository.deleteNews(1L).toList().last()

        assertEquals("Only the author can delete this news", (last as Resource.Error).message)
    }

    @Test
    fun `deleteNews succeeds on 2xx`() = runTest {
        coEvery { api.deleteNews(1L) } returns httpOk(Unit)

        assertTrue(repository.deleteNews(1L).toList().last() is Resource.Success)
    }

    @Test
    fun `reportNews reflects the server response`() = runTest {
        coEvery { api.reportContent(any()) } returnsMany listOf(httpOk(Unit), httpError(500))

        assertTrue(repository.reportNews(1L, "SPAM"))
        assertFalse(repository.reportNews(2L, "ABUSE"))
    }

    @Test
    fun `updateNews maps the updated item`() = runTest {
        coEvery { api.updateNews(1L, any(), any(), null, false, true, null) } returns
            httpOk(news(1, title = "New"))

        val last = repository.updateNews(
            1L, "New", "Text", null, removeVideo = false, imageFile = null, removeImage = true
        ).toList().last()

        assertEquals("New", (last as Resource.Success).data!!.title)
    }

    @Test
    fun `like and unlike return the server counter`() = runTest {
        coEvery { api.likeNews(1L) } returns httpOk(NewsLikeResponse(true, 7))
        coEvery { api.unlikeNews(1L) } returns httpOk(NewsLikeResponse(false, 6))

        assertEquals(7L, (repository.likeNews(1L).toList().last() as Resource.Success).data)
        assertEquals(6L, (repository.unlikeNews(1L).toList().last() as Resource.Success).data)
    }

    @Test
    fun `liked ids are converted to a set`() = runTest {
        coEvery { api.getLikedNewsIds() } returns httpOk(LikedNewsResponse(listOf(1, 2, 2)))

        val last = repository.getLikedNewsIds().toList().last()

        assertEquals(setOf(1L, 2L), (last as Resource.Success).data)
    }

    @Test
    fun `comments are mapped with pagination cursor`() = runTest {
        coEvery { api.getNewsComments(5L, 10L, 20) } returns httpOk(
            NewsCommentsResponse(
                comments = listOf(
                    NewsCommentResponse(11L, 5L, "Hi", "bob", "2026-01-01T00:00:00")
                ),
                total = 1,
                hasMore = false,
                nextBefore = null
            )
        )

        val last = repository.getComments(5L, 10L, 20).toList().last()
        val page = (last as Resource.Success).data!!

        assertEquals("Hi", page.comments.single().content)
        assertEquals(1L, page.total)
    }

    @Test
    fun `addComment maps the created comment`() = runTest {
        coEvery { api.addNewsComment(5L, any()) } returns httpOk(
            NewsCommentResponse(11L, 5L, "Hi", "bob", "2026-01-01T00:00:00")
        )

        val last = repository.addComment(5L, "Hi").toList().last()

        assertEquals("bob", (last as Resource.Success).data!!.author)
    }

    @Test
    fun `deleteComment explains the forbidden case`() = runTest {
        coEvery { api.deleteNewsComment(5L, 11L) } returns httpError(403)

        val last = repository.deleteComment(5L, 11L).toList().last()

        assertEquals(
            "Only the comment or news author can delete this comment",
            (last as Resource.Error).message
        )
    }

    @Test
    fun `reportComment uses the NEWS_COMMENT type`() = runTest {
        coEvery { api.reportContent(any()) } returns httpOk(Unit)

        assertTrue(repository.reportComment(11L, "SPAM"))
        coVerify {
            api.reportContent(match { it.contentType == "NEWS_COMMENT" && it.contentId == 11L })
        }
    }


    @Test
    fun `getNews maps counters and personal like state`() = runTest {
        coEvery { api.getNews(0, 20) } returns httpOk(
            page(
                news(1).copy(
                    views = 10,
                    likes = 2,
                    commentsCount = 3,
                    likedByMe = true
                )
            )
        )

        val item = (repository.getNews().toList().last() as Resource.Success).data!!.single()

        assertEquals(10L, item.views)
        assertEquals(2L, item.likes)
        assertEquals(3L, item.commentsCount)
        assertTrue(item.likedByMe)
    }

    @Test
    fun `updateNews reports forbidden and network errors`() = runTest {
        coEvery { api.updateNews(1L, any(), any(), any(), any(), any(), any()) } returns
            httpError(403)

        val forbidden = repository.updateNews(
            1L, "T", "C", null, false, null, false
        ).toList().last()
        assertEquals("Only the author can edit this news", (forbidden as Resource.Error).message)

        coEvery { api.updateNews(1L, any(), any(), any(), any(), any(), any()) } throws
            IOException("offline")
        val network = repository.updateNews(
            1L, "T", "C", null, false, null, false
        ).toList().last()
        assertEquals("Network error: offline", (network as Resource.Error).message)
    }

    @Test
    fun `updateNews sends the image and remove flags`() = runTest {
        val image = File.createTempFile("news-edit", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        coEvery { api.updateNews(1L, any(), any(), 7L, true, true, any()) } returns
            httpOk(news(1))

        val last = repository.updateNews(
            1L, "T", "C", 7L, removeVideo = true, imageFile = image, removeImage = true
        ).toList().last()

        assertTrue(last is Resource.Success)
        coVerify { api.updateNews(1L, any(), any(), 7L, true, true, any()) }
    }

    @Test
    fun `like and unlike report failures`() = runTest {
        coEvery { api.likeNews(1L) } returns httpError(500)
        coEvery { api.unlikeNews(1L) } throws IOException("offline")

        assertTrue(repository.likeNews(1L).toList().last() is Resource.Error)
        assertTrue(repository.unlikeNews(1L).toList().last() is Resource.Error)
    }

    @Test
    fun `liked ids report failures`() = runTest {
        coEvery { api.getLikedNewsIds() } returns httpError(500)

        assertTrue(repository.getLikedNewsIds().toList().last() is Resource.Error)
    }

    @Test
    fun `comments report failures`() = runTest {
        coEvery { api.getNewsComments(5L, null, 20) } returns httpError(500)
        coEvery { api.addNewsComment(5L, any()) } throws IOException("offline")

        assertTrue(repository.getComments(5L).toList().last() is Resource.Error)
        assertTrue(repository.addComment(5L, "x").toList().last() is Resource.Error)
    }

    @Test
    fun `deleteComment succeeds and reports server errors`() = runTest {
        coEvery { api.deleteNewsComment(5L, 11L) } returns httpOk(Unit)

        assertTrue(repository.deleteComment(5L, 11L).toList().last() is Resource.Success)

        coEvery { api.deleteNewsComment(5L, 12L) } returns httpError(500)
        assertTrue(repository.deleteComment(5L, 12L).toList().last() is Resource.Error)
    }

    @Test
    fun `registerView swallows failures`() = runTest {
        coEvery { api.registerNewsView(1L) } returns httpOk(Unit)
        repository.registerView(1L)
        coVerify { api.registerNewsView(1L) }

        coEvery { api.registerNewsView(2L) } throws IOException("offline")
        repository.registerView(2L)
    }

    @Test
    fun `reportComment reports false on network error`() = runTest {
        coEvery { api.reportContent(any()) } throws IOException("offline")

        assertFalse(repository.reportComment(11L, "SPAM"))
    }

    private fun page(vararg items: NewsResponse) = PageResponse(
        content = items.toList(),
        empty = items.isEmpty(),
        first = true,
        last = true,
        number = 0,
        numberOfElements = items.size,
        size = 20,
        totalElements = items.size.toLong(),
        totalPages = 1
    )

    private fun news(id: Long, title: String = "News $id") = NewsResponse(
        id = id,
        title = title,
        content = "Text $id",
        imageUrl = null,
        relatedVideo = null,
        author = "vovan",
        publishedAt = "2026-01-01T00:00:00"
    )
}
