package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.api.KlipyApi
import com.example.muzea.data.model.KlipyGifsResponse
import com.example.muzea.data.model.KlipyPage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * Ветки [GifRepositoryImpl]: Klipy-ветвление search/trending, деградация к
 * пустому списку при сетевой ошибке и проброс отмены из кэширующей обёртки.
 */
class GifRepositoryFailureTest {

    private val klipyApi = mockk<KlipyApi>()
    private val chatApiService = mockk<ChatApiService>()
    private val chatAuthManager = mockk<ChatAuthManager>(relaxed = true)
    private val gifCache = GifCache(File.createTempFile("gif-fail", ".json").apply { delete() })

    private val repository =
        GifRepositoryImpl(klipyApi, chatApiService, chatAuthManager, gifCache)

    private fun emptyKlipyPage() = Response.success(
        KlipyGifsResponse(result = true, data = KlipyPage(items = emptyList()))
    )

    @Before
    fun setUp() {
        gifCache.clear()
        coEvery { chatAuthManager.isAuthenticated() } returns true
    }

    @Test
    fun searchWithAQueryUsesTheSearchEndpoint() = runTest {
        coEvery { klipyApi.search(any(), "cat", 1, 30, "high") } returns emptyKlipyPage()

        assertTrue(repository.search("cat", 0, 30).isEmpty())
    }

    @Test
    fun blankQueryUsesTheTrendingEndpoint() = runTest {
        coEvery { klipyApi.trending(any(), 1, 30, "high") } returns emptyKlipyPage()

        assertTrue(repository.search("", 0, 30).isEmpty())
    }

    @Test
    fun searchNetworkErrorDegradesToEmpty() = runTest {
        coEvery { klipyApi.search(any(), "cat", 1, 30, "high") } throws IOException("offline")

        assertTrue(repository.search("cat", 0, 30).isEmpty())
    }

    @Test
    fun searchPropagatesCancellation() = runTest {
        coEvery { klipyApi.search(any(), "cat", 1, 30, "high") } throws
            CancellationException("stop")

        var cancelled = false
        try {
            repository.search("cat", 0, 30)
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun recentNetworkErrorDegradesToEmpty() = runTest {
        coEvery { chatApiService.getRecentGifs(0, 30) } throws IOException("offline")

        assertTrue(repository.recent(0, 30).isEmpty())
    }

    @Test
    fun recentPropagatesCancellation() = runTest {
        coEvery { chatApiService.getRecentGifs(0, 30) } throws CancellationException("stop")

        var cancelled = false
        try {
            repository.recent(0, 30)
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun chatGifsNetworkErrorDegradesToEmpty() = runTest {
        coEvery { chatApiService.getChatGifs("c1", 0, 30) } throws IOException("offline")

        assertTrue(repository.chatGifs("c1", 0, 30).isEmpty())
    }
}
