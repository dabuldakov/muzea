package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.FinalizeVideoUploadRequest
import com.example.muzea.data.model.PresignUploadRequest
import com.example.muzea.data.model.PresignUploadResponse
import com.example.muzea.data.model.VideoResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

class VideoRepositoryImplTest {

    @get:Rule
    val uploadServer = MockWebServer()

    private val api = mockk<ApiService>()
    private val repository = VideoRepositoryImpl(api, PresignedUploader(OkHttpClient()))

    @Before
    fun setUp() {
        VideoListCache.clear()
    }

    private fun videoFile(bytes: Int = 32): File =
        File.createTempFile("clip", ".mp4").apply { writeBytes(ByteArray(bytes)) }

    private fun presign() = PresignUploadResponse(
        uploadUrl = uploadServer.url("/videos/obj.mp4").toString(),
        objectKey = "obj.mp4",
        expiresInSeconds = 900
    )

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
    fun `uploadVideo uploads directly to storage and finalizes the object`() = runTest {
        val file = videoFile()
        val progress = mutableListOf<Int>()
        coEvery { api.presignVideoUpload(PresignUploadRequest("video/mp4", file.length())) } returns
            httpOk(presign())
        coEvery {
            api.finalizeVideoUpload(FinalizeVideoUploadRequest("obj.mp4", "Clip", "desc"))
        } returns httpOk(video(3))
        uploadServer.enqueue(MockResponse().setResponseCode(200))

        val last = repository
            .uploadVideo("Clip", "desc", file, "video/mp4", null) { progress += it }
            .toList().last()

        assertEquals(3L, (last as Resource.Success).data!!.id)
        assertEquals(100, progress.last())
        val put = uploadServer.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("video/mp4", put.getHeader("Content-Type"))
        assertEquals(file.length(), put.bodySize)
        coVerify(exactly = 1) {
            api.finalizeVideoUpload(FinalizeVideoUploadRequest("obj.mp4", "Clip", "desc"))
        }
        coVerify(exactly = 0) { api.uploadVideo(any(), any(), any(), any()) }
    }

    @Test
    fun `uploadVideo falls back to multipart when presigned is disabled`() = runTest {
        coEvery { api.presignVideoUpload(any()) } returns httpError(503)
        coEvery { api.uploadVideo(any(), any(), any(), any()) } returns httpOk(video(4))

        val last = repository
            .uploadVideo("Clip", null, videoFile(), "video/mp4", null) {}
            .toList().last()

        assertEquals(4L, (last as Resource.Success).data!!.id)
        coVerify(exactly = 1) { api.uploadVideo(any(), any(), any(), null) }
    }

    @Test
    fun `uploadVideo reports a rejected presign`() = runTest {
        coEvery { api.presignVideoUpload(any()) } returns httpError(400)

        val last = repository
            .uploadVideo("Clip", null, videoFile(), "video/mp4", null) {}
            .toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Upload failed"))
        coVerify(exactly = 0) { api.uploadVideo(any(), any(), any(), any()) }
    }

    @Test
    fun `uploadVideo reports a failed storage PUT`() = runTest {
        coEvery { api.presignVideoUpload(any()) } returns httpOk(presign())
        uploadServer.enqueue(MockResponse().setResponseCode(403))

        val last = repository
            .uploadVideo("Clip", null, videoFile(), "video/mp4", null) {}
            .toList().last()

        assertEquals("Upload failed: HTTP 403", (last as Resource.Error).message)
        coVerify(exactly = 0) { api.finalizeVideoUpload(any()) }
    }

    @Test
    fun `uploadVideo reports a failed finalize`() = runTest {
        coEvery { api.presignVideoUpload(any()) } returns httpOk(presign())
        coEvery { api.finalizeVideoUpload(any()) } returns httpError(409)
        uploadServer.enqueue(MockResponse().setResponseCode(200))

        val last = repository
            .uploadVideo("Clip", null, videoFile(), "video/mp4", null) {}
            .toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Upload failed"))
    }

    @Test
    fun `uploadVideo reports a network error`() = runTest {
        coEvery { api.presignVideoUpload(any()) } throws IOException("offline")

        val last = repository
            .uploadVideo("Clip", null, videoFile(), "video/mp4", null) {}
            .toList().last()

        assertEquals("Network error: offline", (last as Resource.Error).message)
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
