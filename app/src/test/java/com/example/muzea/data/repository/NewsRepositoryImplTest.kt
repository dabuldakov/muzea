package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.NewsCreateResponse
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
