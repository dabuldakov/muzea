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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Интеграционный тест видео против MockWebServer.
 * Сервер отдаёт thumbnailUrl абсолютным, а url стрима — относительным.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRepositoryIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private val repository get() = VideoRepository(IntegrationTestClient.mainApi(server))

    private val liveLikeVideo = """
        [{"id":29,"title":"tomsk","description":"Tomsk city",
          "url":"/api/videos/stream/77b0e3ac-137a-4e47-bee4-011294332842.mp4",
          "thumbnailUrl":"https://api-muzea.su/api/videos/thumbnail/0e8c7c4f-9eb4-463f-b498-39ed65dc00f0.jpeg",
          "fileSize":29683941,"duration":null,"views":4,"likes":0,
          "uploadedBy":"dabuldakov","uploadedAt":"2026-09-20T04:05:25.802792"}]
    """.trimIndent()

    @Test
    fun `getVideos parses absolute thumbnail and relative stream url`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeVideo)
        )

        val result = repository.getVideos().toList().last()

        assertTrue(result is NetworkResult.Success)
        val video = result.data!!.single()
        assertEquals(
            "https://api-muzea.su/api/videos/thumbnail/0e8c7c4f-9eb4-463f-b498-39ed65dc00f0.jpeg",
            video.thumbnailUrl
        )
        assertEquals("/api/videos/stream/77b0e3ac-137a-4e47-bee4-011294332842.mp4", video.url)

        // Адаптер строит превью через MediaUrl — не должен дублировать хост.
        assertEquals(video.thumbnailUrl, MediaUrl.main(video.thumbnailUrl))
        // А плеер строит стрим через MediaUrl — относительный адрес должен склеиваться с базой.
        assertEquals(
            "https://api-muzea.su/api/videos/stream/77b0e3ac-137a-4e47-bee4-011294332842.mp4",
            MediaUrl.main(video.url)
        )

        val request = server.takeRequest()
        assertEquals("/api/videos", request.path)
    }

    @Test
    fun `getVideoById parses single video`() = runTest {
        val single = """
            {"id":29,"title":"tomsk","description":null,
             "url":"/api/videos/stream/77b0e3ac-137a-4e47-bee4-011294332842.mp4",
             "thumbnailUrl":null,"fileSize":29683941,"duration":null,
             "views":4,"likes":0,"uploadedBy":"dabuldakov","uploadedAt":"2026-09-20T04:05:25.802792"}
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(single)
        )

        val result = repository.getVideoById(29).toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(29L, result.data!!.id)
        assertEquals("tomsk", result.data!!.title)
        assertTrue(result.data!!.thumbnailUrl.isNullOrEmpty())

        val request = server.takeRequest()
        assertEquals("/api/videos/29", request.path)
    }
}