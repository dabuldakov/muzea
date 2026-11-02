package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.AttachmentResponse
import com.example.muzea.data.model.ChatReceiptResponse
import com.example.muzea.data.model.PageResponse
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.domain.model.Gif
import com.example.muzea.utils.PreparedAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class MessageRepositoryImplTest {

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
    }

    @Test
    fun `unauthenticated load returns an error without a request`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val results = repository.loadMessages("chat-1").toList()

        assertTrue(results.first() is Resource.Loading)
        assertTrue((results.last() as Resource.Error).message!!.contains("Chat auth failed"))
        coVerify(exactly = 0) { api.getMessages(any()) }
    }

    @Test
    fun `loadMessages maps the page and fills the cache`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMessages("chat-1") } returns httpOk(page(message("m1"), message("m2")))

        val results = repository.loadMessages("chat-1").toList()

        val data = (results.last() as Resource.Success).data!!
        assertEquals(listOf("m1", "m2"), data.map { it.messageUuid })
        assertEquals(listOf("m1", "m2"), repository.cachedMessages("chat-1").map { it.messageUuid })
    }

    @Test
    fun `loadMessages retries once after 401 and invalidates the token`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMessages("chat-1") } returnsMany listOf(
            httpError(401),
            httpOk(page(message("m1")))
        )

        val results = repository.loadMessages("chat-1").toList()

        assertTrue(results.last() is Resource.Success)
        coVerify(exactly = 2) { api.getMessages("chat-1") }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `loadMessages turns a server error into Resource Error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMessages("chat-1") } returns httpError(500)

        val error = repository.loadMessages("chat-1").toList().last() as Resource.Error

        assertTrue(error.message!!.startsWith("Failed to load messages"))
        assertTrue(repository.cachedMessages("chat-1").isEmpty())
    }

    @Test
    fun `loadMessages turns a network exception into Resource Error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMessages("chat-1") } throws IOException("boom")

        val error = repository.loadMessages("chat-1").toList().last() as Resource.Error

        assertEquals("Network error: boom", error.message)
    }

    @Test
    fun `loadMessagesBefore prepends older messages without duplicates`() = runTest {
        ChatMessagesCache.put("chat-1", listOf(message("m2").toDomain()))
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMessagesBefore("chat-1", "m2", 50) } returns
            httpOk(listOf(message("m1"), message("m2")))

        val result = repository.loadMessagesBefore("chat-1", "m2", 50).toList().last()

        assertTrue(result is Resource.Success)
        assertEquals(
            listOf("m1", "m2"),
            repository.cachedMessages("chat-1").map { it.messageUuid }
        )
    }

    @Test
    fun `sendMessage returns the created message`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.sendMessage("chat-1", any()) } returns httpOk(message("m3", text = "hello"))

        val result = repository.sendMessage("chat-1", "hello").toList().last()

        assertEquals("m3", (result as Resource.Success).data!!.messageUuid)
        assertEquals("hello", result.data!!.text)
    }

    @Test
    fun `sendGif sends a GIF message with provider metadata`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessage("chat-1", capture(request)) } returns httpOk(message("m4"))

        repository.sendGif("chat-1", gif()).toList()

        assertEquals("GIF", request.captured.messageType)
        assertEquals("", request.captured.text)
        assertEquals("klipy", request.captured.gif?.provider)
        assertEquals("gif-1", request.captured.gif?.providerGifId)
        assertEquals("https://cdn/gif.mp4", request.captured.gif?.mp4Url)
    }

    @Test
    fun `reportMessage returns true on success and false on error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.reportContent(any()) } returnsMany listOf(httpOk(Unit), httpError(400))

        assertTrue(repository.reportMessage("m1", "SPAM"))
        assertFalse(repository.reportMessage("m2", "ABUSE"))
    }

    @Test
    fun `reportMessage returns false when the request throws`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.reportContent(any()) } throws IOException("offline")

        assertFalse(repository.reportMessage("m1", "SPAM"))
    }

    @Test
    fun `blockUser returns true on success and false on error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.blockUser(any()) } returnsMany listOf(httpOk(Unit), httpError(500))

        assertTrue(repository.blockUser("user-1"))
        assertFalse(repository.blockUser("user-2"))
    }

    private fun page(vararg messages: MessageResponse) = PageResponse(
        content = messages.toList(),
        empty = messages.isEmpty(),
        first = true,
        last = true,
        number = 0,
        numberOfElements = messages.size,
        size = 50,
        totalElements = messages.size.toLong(),
        totalPages = 1
    )

    private fun message(uuid: String, text: String? = null) = MessageResponse(
        messageUuid = uuid,
        chatUuid = "chat-1",
        senderId = 1L,
        senderUuid = "user-1",
        senderName = "Anna",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = null,
        updatedAt = null
    )

    private fun gif() = Gif(
        provider = "klipy",
        providerGifId = "gif-1",
        title = "gif",
        url = "https://cdn/gif.gif",
        previewUrl = "https://cdn/gif.jpg",
        width = 100,
        height = 200,
        mp4Url = "https://cdn/gif.mp4"
    )

    @Test
    fun `markMessagesAsDelivered returns true on success and retries after 401`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsDelivered("chat-1", "m1") } returnsMany listOf(
            httpError(401),
            httpOk(Unit)
        )

        assertTrue(repository.markMessagesAsDelivered("chat-1", "m1"))
        coVerify(exactly = 2) { api.markMessagesAsDelivered("chat-1", "m1") }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `markMessagesAsDelivered fails without a token or on error`() = runTest {
        coEvery { auth.isAuthenticated() } returns false
        assertFalse(repository.markMessagesAsDelivered("chat-1", "m1"))

        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsDelivered("chat-1", "m2") } returns httpError(500)
        assertFalse(repository.markMessagesAsDelivered("chat-1", "m2"))
    }

    @Test
    fun `loadChatReceipts maps participant watermarks`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChatReceipts("chat-1") } returns httpOk(
            listOf(
                ChatReceiptResponse(
                    userUuid = "u1",
                    username = "anna",
                    fullName = "Anna",
                    avatarUrl = null,
                    joinedAt = "2026-01-01T10:00:00",
                    lastDeliveredMessageId = 12,
                    lastDeliveredAt = "2026-01-01T10:01:00",
                    lastReadMessageId = 11,
                    lastReadAt = "2026-01-01T10:02:00"
                )
            )
        )

        val receipts = repository.loadChatReceipts("chat-1")!!

        assertEquals(1, receipts.size)
        assertEquals("u1", receipts.single().userUuid)
        assertEquals(12L, receipts.single().lastDeliveredMessageId)
        assertEquals(11L, receipts.single().lastReadMessageId)
    }

    @Test
    fun `loadChatReceipts returns null on http error and exception`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChatReceipts("chat-1") } returns httpError(500)

        assertNull(repository.loadChatReceipts("chat-1"))

        coEvery { api.getChatReceipts("chat-1") } throws IOException("offline")

        assertNull(repository.loadChatReceipts("chat-1"))
    }

    @Test
    fun `uploadAttachment returns the server attachment`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } returns httpOk(
            AttachmentResponse(
                attachmentUuid = "a-1",
                fileUrl = "attachments/a-1.jpg",
                thumbnailUrl = null,
                type = "IMAGE",
                width = 100,
                height = 200
            )
        )
        val file = File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val prepared = PreparedAttachment(
            file = file,
            thumbnail = null,
            name = "photo.jpg",
            size = file.length(),
            mimeType = "image/jpeg",
            type = "IMAGE",
            width = 100,
            height = 200,
            duration = null
        )

        val result = repository.uploadAttachment("chat-1", prepared)

        assertEquals("a-1", (result as Resource.Success).data!!.attachmentUuid)
        assertEquals("IMAGE", result.data!!.type)
    }

    @Test
    fun `uploadAttachment reports a rejected file`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadAttachment(any(), any(), any(), any(), any(), any()) } returns httpError(400)
        val prepared = PreparedAttachment(
            file = File.createTempFile("bad", ".exe"),
            thumbnail = null,
            name = "bad.exe",
            size = 1,
            mimeType = "application/octet-stream",
            type = "DOCUMENT",
            width = null,
            height = null,
            duration = null
        )

        val result = repository.uploadAttachment("chat-1", prepared)

        assertTrue(result is Resource.Error)
    }
}
