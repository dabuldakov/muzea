package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.api.KlipyApi
import com.example.muzea.data.model.GifResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class GifRepositoryCacheTest {

    private val klipyApi = mockk<KlipyApi>(relaxed = true)
    private val chatApiService = mockk<ChatApiService>()
    private val chatAuthManager = mockk<ChatAuthManager>()
    private val gifCache = GifCache(java.io.File.createTempFile("gif-repo-cache", ".json").apply { delete() })

    private val repository = GifRepositoryImpl(klipyApi, chatApiService, chatAuthManager, gifCache)

    private fun response(id: String) = GifResponse(
        provider = "klipy", providerGifId = id, title = null,
        url = "u$id", previewUrl = null, width = null, height = null
    )

    @Before
    fun setUp() {
        gifCache.clear()
        coEvery { chatAuthManager.isAuthenticated() } returns true
    }

    @After
    fun tearDown() = gifCache.clear()

    @Test
    fun `recent second call is served from cache`() = runTest {
        coEvery { chatApiService.getRecentGifs(0, 30) } returns Response.success(listOf(response("a")))

        val first = repository.recent(0, 30)
        val second = repository.recent(0, 30)

        assertEquals(listOf("a"), first.map { it.providerGifId })
        assertEquals(listOf("a"), second.map { it.providerGifId })
        coVerify(exactly = 1) { chatApiService.getRecentGifs(0, 30) }
    }

    @Test
    fun `chat gifs cached per chat`() = runTest {
        coEvery { chatApiService.getChatGifs("c1", 0, 30) } returns Response.success(listOf(response("g1")))

        repository.chatGifs("c1", 0, 30)
        repository.chatGifs("c1", 0, 30)

        coVerify(exactly = 1) { chatApiService.getChatGifs("c1", 0, 30) }
    }

    @Test
    fun `pages after the first are never cached`() = runTest {
        coEvery { chatApiService.getRecentGifs(1, 30) } returns Response.success(listOf(response("b")))

        repository.recent(1, 30)
        repository.recent(1, 30)

        coVerify(exactly = 2) { chatApiService.getRecentGifs(1, 30) }
    }

    @Test
    fun `first page failure without cache returns an empty list`() = runTest {
        coEvery { chatApiService.getRecentGifs(0, 30) } returns
            Response.error(500, "boom".toResponseBody())

        assertTrue(repository.recent(0, 30).isEmpty())
    }

    @Test
    fun `chat gifs failure returns an empty list`() = runTest {
        coEvery { chatApiService.getChatGifs("c1", 0, 30) } returns
            Response.error(500, "boom".toResponseBody())

        assertTrue(repository.chatGifs("c1", 0, 30).isEmpty())
    }
}
