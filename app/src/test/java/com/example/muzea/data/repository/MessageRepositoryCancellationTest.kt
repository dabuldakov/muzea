package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.AttachmentResponse
import com.example.muzea.domain.model.Gif
import com.example.muzea.utils.PreparedAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Ветки MessageRepositoryImpl, которые обычные тесты не трогают:
 * проброс отмены (CancellationException) во всех методах и редкие ветки
 * загрузки вложений (миниатюра, пустой ответ, отсутствие авторизации).
 */
class MessageRepositoryCancellationTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private lateinit var gifCache: GifCache
    private lateinit var repository: MessageRepositoryImpl

    @Before
    fun setUp() {
        ChatMessagesCache.clear()
        gifCache = GifCache(File.createTempFile("gif-cache", ".json").apply { delete() })
        repository = MessageRepositoryImpl(api, auth, gifCache, context)
        coEvery { auth.isAuthenticated() } returns true
    }

    /** Возвращает true, если блок пробросил CancellationException наружу. */
    private inline fun cancellationThrown(block: () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    @Test
    fun `loadMessages propagates cancellation`() = runTest {
        coEvery { api.getMessages("chat-1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.loadMessages("chat-1").toList() })
    }

    @Test
    fun `loadMessagesBefore propagates cancellation`() = runTest {
        coEvery { api.getMessagesBefore("chat-1", "m1", 50) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.loadMessagesBefore("chat-1", "m1", 50).toList() })
    }

    @Test
    fun `sendMessage propagates cancellation`() = runTest {
        coEvery { api.sendMessage("chat-1", any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.sendMessage("chat-1", "hi").toList() })
    }

    @Test
    fun `sendGif propagates cancellation`() = runTest {
        coEvery { api.sendMessage("chat-1", any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.sendGif("chat-1", gif()).toList() })
    }

    @Test
    fun `editMessage propagates cancellation`() = runTest {
        coEvery { api.editMessage("m1", any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.editMessage("m1", "x").toList() })
    }

    @Test
    fun `deleteMessage propagates cancellation`() = runTest {
        coEvery { api.deleteMessage("m1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.deleteMessage("m1") })
    }

    @Test
    fun `forwardMessage propagates cancellation`() = runTest {
        coEvery { api.forwardMessage(any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.forwardMessage("m1", "chat-2").toList() })
    }

    @Test
    fun `pin and unpin propagate cancellation`() = runTest {
        coEvery { api.pinMessage("chat-1", "m1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.pinMessage("chat-1", "m1") })

        coEvery { api.unpinMessage("chat-1", "m1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.unpinMessage("chat-1", "m1") })
    }

    @Test
    fun `loadPinned propagates cancellation`() = runTest {
        coEvery { api.getPinnedMessages("chat-1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.loadPinned("chat-1") })
    }

    @Test
    fun `read and delivered markers propagate cancellation`() = runTest {
        coEvery { api.markMessagesAsRead("chat-1", "m1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.markMessagesAsRead("chat-1", "m1") })

        coEvery { api.markMessagesAsDelivered("chat-1", "m1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.markMessagesAsDelivered("chat-1", "m1") })
    }

    @Test
    fun `loadChatReceipts propagates cancellation`() = runTest {
        coEvery { api.getChatReceipts("chat-1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.loadChatReceipts("chat-1") })
    }

    @Test
    fun `report and block propagate cancellation`() = runTest {
        coEvery { api.reportContent(any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.reportMessage("m1", "SPAM") })

        coEvery { api.blockUser(any()) } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.blockUser("u1") })
    }

    @Test
    fun `uploadAttachment propagates cancellation`() = runTest {
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } throws
            CancellationException("stop")
        assertTrue(cancellationThrown { repository.uploadAttachment("chat-1", prepared()) })
    }

    @Test
    fun `downloadAttachment propagates cancellation`() = runTest {
        coEvery { api.downloadAttachment("a-1") } throws CancellationException("stop")
        assertTrue(cancellationThrown { repository.downloadAttachment("a-1", null) })
    }

    @Test
    fun `uploadAttachment without auth returns an error and skips the request`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val result = repository.uploadAttachment("chat-1", prepared())

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { api.uploadAttachment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uploadAttachment sends the optional thumbnail`() = runTest {
        val thumb = File.createTempFile("thumb", ".jpg").apply { deleteOnExit() }
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } returns httpOk(
            AttachmentResponse(
                attachmentUuid = "a-1", fileUrl = "/api/attachments/a-1", thumbnailUrl = null,
                type = "IMAGE", width = 1, height = 1
            )
        )

        val result = repository.uploadAttachment("chat-1", prepared(thumbnail = thumb))

        assertTrue(result is Resource.Success)
        assertEquals("a-1", (result as Resource.Success).data!!.attachmentUuid)
    }

    @Test
    fun `uploadAttachment reports an empty body as an error`() = runTest {
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } returns httpOk(
            AttachmentResponse(
                attachmentUuid = null, fileUrl = null, thumbnailUrl = null,
                type = "IMAGE", width = null, height = null
            )
        )

        val result = repository.uploadAttachment("chat-1", prepared())

        assertEquals("Server returned an empty attachment", (result as Resource.Error).message)
    }

    @Test
    fun `downloadAttachment writes an anonymous name when none is given`() = runTest {
        every { context.filesDir } returns File.createTempFile("files", "").apply {
            delete(); mkdirs(); deleteOnExit()
        }
        coEvery { api.downloadAttachment("a-1") } returns httpOk(
            "hello".toResponseBody("text/plain".toMediaTypeOrNull())
        )

        val file = repository.downloadAttachment("a-1", null)

        assertEquals("file", file!!.name)
        assertEquals("hello", file.readText())
    }

    private fun gif() = Gif(
        provider = "klipy", providerGifId = "g1", title = null,
        url = "https://c/1.gif", previewUrl = null, width = 1, height = 1
    )

    private fun prepared(thumbnail: File? = null) = PreparedAttachment(
        file = File.createTempFile("upload", ".bin").apply {
            writeBytes(ByteArray(8)); deleteOnExit()
        },
        thumbnail = thumbnail,
        name = "upload.bin",
        size = 8,
        mimeType = "application/octet-stream",
        type = "FILE",
        width = null,
        height = null,
        duration = null
    )
}
