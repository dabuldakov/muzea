package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoRepositoryCacheTest {

    @get:Rule
    val server = MockWebServer()

    private val repository get() = VideoRepository(IntegrationTestClient.mainApi(server))

    private val videosJson = """
        [{"id":29,"title":"tomsk","description":null,
          "url":"/api/videos/stream/a.mp4","thumbnailUrl":null,
          "fileSize":1,"duration":null,"views":4,"likes":0,
          "uploadedBy":"dabuldakov","uploadedAt":"2026-09-20T04:05:25"}]
    """.trimIndent()

    @Before
    fun setUp() = VideoListCache.clear()

    @After
    fun tearDown() = VideoListCache.clear()

    @Test
    fun `getVideos caches the list and cachedVideos returns it`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(videosJson)
        )

        val result = repository.getVideos().toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(listOf(29L), VideoListCache.get().map { it.id })
        assertEquals(listOf(29L), repository.cachedVideos().map { it.id })
    }

    @Test
    fun `failed load does not populate the cache`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getVideos().toList().last()

        assertTrue(result is NetworkResult.Error)
        assertTrue(VideoListCache.get().isEmpty())
    }
}
