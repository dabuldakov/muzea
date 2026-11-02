package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.VideoResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class VideoRepositoryImplTest {

    private val api = mockk<ApiService>()
    private val repository = VideoRepositoryImpl(api)

    @Before
    fun setUp() {
        VideoListCache.clear()
    }

    @Test
    fun `getVideos maps the list and fills the cache`() = runTest {
        coEvery { api.getVideos() } returns httpOk(listOf(video(1), video(2)))

        val last = repository.getVideos().toList().last()

        assertEquals(listOf(1L, 2L), (last as Resource.Success).data!!.map { it.id })
        assertEquals(listOf(1L, 2L), repository.cachedVideos().map { it.id })
    }

    @Test
    fun `getVideos reports a failure`() = runTest {
        coEvery { api.getVideos() } returns httpError(500)

        val last = repository.getVideos().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load videos"))
        assertTrue(repository.cachedVideos().isEmpty())
    }

    @Test
    fun `getVideoById maps a single video`() = runTest {
        coEvery { api.getVideoById(1L) } returns httpOk(video(1, title = "Clip"))

        val last = repository.getVideoById(1L).toList().last()

        assertEquals("Clip", (last as Resource.Success).data!!.title)
    }

    @Test
    fun `getVideoById reports a network error`() = runTest {
        coEvery { api.getVideoById(1L) } throws IOException("boom")

        val last = repository.getVideoById(1L).toList().last()

        assertEquals("Network error: boom", (last as Resource.Error).message)
    }

    @Test
    fun `uploadVideo posts multipart data and maps the result`() = runTest {
        coEvery { api.uploadVideo(any(), any(), any(), any()) } returns httpOk(video(3))
        val file = MultipartBody.Part.createFormData(
            "file", "clip.mp4", "data".toRequestBody()
        )

        val last = repository.uploadVideo("Clip", null, file, null).toList().last()

        assertEquals(3L, (last as Resource.Success).data!!.id)
        coVerify(exactly = 1) { api.uploadVideo(any(), any(), file, null) }
    }

    @Test
    fun `uploadVideo reports a failure`() = runTest {
        coEvery { api.uploadVideo(any(), any(), any(), any()) } returns httpError(413)
        val file = MultipartBody.Part.createFormData("file", "clip.mp4", "".toRequestBody())

        val last = repository.uploadVideo("Clip", null, file, null).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Upload failed"))
    }

    @Test
    fun `deleteVideo succeeds on 2xx and reports errors`() = runTest {
        coEvery { api.deleteVideo(any()) } returnsMany listOf(httpOk(Unit), httpError(500))

        assertTrue(repository.deleteVideo(1L).toList().last() is Resource.Success)
        assertTrue(repository.deleteVideo(2L).toList().last() is Resource.Error)
    }

    @Test
    fun `reportVideo reflects the server response`() = runTest {
        coEvery { api.reportContent(any()) } returnsMany listOf(httpOk(Unit), httpError(400))

        assertTrue(repository.reportVideo(1L, "SPAM"))
        assertFalse(repository.reportVideo(2L, "OTHER"))
    }

    @Test
    fun `reportVideo swallows network failures`() = runTest {
        coEvery { api.reportContent(any()) } throws IOException("offline")

        assertFalse(repository.reportVideo(1L, "SPAM"))
    }

    private fun video(id: Long, title: String = "Video $id") = VideoResponse(
        id = id,
        title = title,
        description = null,
        url = "/api/videos/stream/$id.mp4",
        thumbnailUrl = null,
        fileSize = 1024L,
        duration = null,
        views = 0,
        likes = 0,
        uploadedBy = "vovan",
        uploadedAt = "2026-01-01T00:00:00"
    )
}
