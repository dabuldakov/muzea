package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.AttachmentResponse
import com.example.muzea.data.model.PresignAttachmentResponse
import com.example.muzea.utils.PreparedAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Presigned-загрузка вложений чата и откат на multipart. */
class AttachmentPresignUploadTest {

    @get:Rule
    val uploadServer = MockWebServer()

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = MessageRepositoryImpl(
        api,
        auth,
        GifCache(File.createTempFile("gif-cache", ".json").apply { delete() }),
        mockk(relaxed = true)
    )

    private fun prepared(withThumbnail: Boolean = false): PreparedAttachment {
        val file = File.createTempFile("clip", ".mp4").apply { writeBytes(ByteArray(64)) }
        val thumbnail = if (withThumbnail) {
            File.createTempFile("thumb", ".jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            }
        } else {
            null
        }
        return PreparedAttachment(
            file = file,
            thumbnail = thumbnail,
            name = "clip.mp4",
            size = file.length(),
            mimeType = "video/mp4",
            type = "VIDEO",
            width = 1080,
            height = 1920,
            duration = 31
        )
    }

    private fun attachment() = AttachmentResponse(
        attachmentUuid = "11111111-1111-1111-1111-111111111111",
        fileUrl = "attachments/obj.mp4",
        thumbnailUrl = null,
        type = "VIDEO",
        width = 1080,
        height = 1920
    )

    @Test
    fun `uploads the file directly and finalizes it`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery {
            api.presignAttachmentUpload(any())
        } returns httpOk(
            PresignAttachmentResponse(
                uploadUrl = uploadServer.url("/attachments/obj.mp4").toString(),
                objectKey = "attachments/obj.mp4",
                expiresInSeconds = 900
            )
        )
        coEvery { api.finalizeAttachment(any()) } returns httpOk(attachment())
        uploadServer.enqueue(MockResponse().setResponseCode(200))

        val result = repository.uploadAttachment("chat-1", prepared()) {}

        assertTrue(result is Resource.Success)
        val put = uploadServer.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("video/mp4", put.getHeader("Content-Type"))
        coVerify(exactly = 1) {
            api.finalizeAttachment(match {
                it.objectKey == "attachments/obj.mp4" &&
                    it.thumbnailKey == null &&
                    it.fileName == "clip.mp4"
            })
        }
        coVerify(exactly = 0) { api.uploadAttachment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uploads the thumbnail and passes its key to finalize`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery {
            api.presignAttachmentUpload(match { it.kind == "ATTACHMENT" })
        } returns httpOk(
            PresignAttachmentResponse(
                uploadUrl = uploadServer.url("/attachments/obj.mp4").toString(),
                objectKey = "attachments/obj.mp4",
                expiresInSeconds = 900
            )
        )
        coEvery {
            api.presignAttachmentUpload(match { it.kind == "THUMBNAIL" })
        } returns httpOk(
            PresignAttachmentResponse(
                uploadUrl = uploadServer.url("/attachment-thumbnails/thumb.jpg").toString(),
                objectKey = "attachment-thumbnails/thumb.jpg",
                expiresInSeconds = 900
            )
        )
        coEvery { api.finalizeAttachment(any()) } returns httpOk(attachment())
        uploadServer.enqueue(MockResponse().setResponseCode(200))
        uploadServer.enqueue(MockResponse().setResponseCode(200))

        val result = repository.uploadAttachment("chat-1", prepared(withThumbnail = true)) {}

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) {
            api.finalizeAttachment(match {
                it.thumbnailKey == "attachment-thumbnails/thumb.jpg"
            })
        }
    }

    @Test
    fun `falls back to multipart when presigned is disabled`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.presignAttachmentUpload(any()) } returns httpError(503)
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } returns
            httpOk(attachment())

        val result = repository.uploadAttachment("chat-1", prepared()) {}

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { api.uploadAttachment(any(), any(), null, any(), any(), any()) }
    }
}
