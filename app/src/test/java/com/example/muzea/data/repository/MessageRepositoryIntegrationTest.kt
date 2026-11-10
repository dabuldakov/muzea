package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.IntegrationTestClient
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Интеграционные тесты действий над сообщением против MockWebServer:
 * редактирование, удаление, пересылка и закрепление. Закрепляют контракт
 * живого сервера — пути, HTTP-методы и JSON-поля запросов/ответов.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)

    private fun repository() = MessageRepositoryImpl(
        IntegrationTestClient.chatApi(server),
        auth,
        GifCache(File.createTempFile("gif-cache", ".json").apply { delete() }),
        context
    )

    private val liveLikeMessage = """
        {"messageUuid":"3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33",
         "messageId":101,
         "chatUuid":"7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f",
         "senderId":4,
         "senderUuid":"2afcbb98-85bd-4a24-be7d-5e66dbe53933",
         "senderName":"itest_audit_02","senderAvatar":null,
         "text":"edited text","messageType":"TEXT","replyToMessageUuid":null,
         "isEdited":true,"isDeleted":false,"isPinned":false,
         "createdAt":"2026-09-20T07:03:22","updatedAt":"2026-09-20T07:05:00"}
    """.trimIndent()

    @Test
    fun `editMessage puts the new text and parses the updated message`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeMessage)
        )

        val result = repository()
            .editMessage("3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", "edited text")
            .toList()
            .last()

        assertTrue(result is Resource.Success)
        assertEquals("edited text", result.data!!.text)
        assertTrue(result.data!!.isEdited)

        val request = server.takeRequest()
        assertEquals("/api/messages/3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", request.path)
        assertEquals("PUT", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("edited text", body.get("text").asString)
    }

    @Test
    fun `sendMessage posts power and maps it back`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"messageUuid":"9c1e5d22-11aa-4b7c-8d3e-0f9a7b6c5d4e",
                         "chatUuid":"7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f",
                         "senderId":4,"senderUuid":"me","senderName":"me","senderAvatar":null,
                         "text":"hi","messageType":"TEXT","replyToMessageUuid":null,
                         "isEdited":false,"isDeleted":false,"isPinned":false,
                         "createdAt":"2026-11-10T10:00:00","updatedAt":null,
                         "power":77}"""
                )
        )

        val result = repository().sendMessage(
            "7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f", "hi", emptyList(), "TEXT",
            null, "c-1", 77
        ).toList().last()

        assertTrue(result is Resource.Success)
        assertEquals(77, result.data!!.power)
        val body = JsonParser().parse(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(77, body.get("power").asInt)
    }

    @Test
    fun `deleteMessage sends DELETE and reports success`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200))

        val deleted = repository().deleteMessage("3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33")

        assertTrue(deleted)
        val request = server.takeRequest()
        assertEquals("/api/messages/3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", request.path)
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `deleteMessage reports a server failure`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(404))

        assertFalse(repository().deleteMessage("missing"))
    }

    @Test
    fun `forwardMessage posts uuid and target chat and parses the copy`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"messageUuid":"9c1e5d22-11aa-4b7c-8d3e-0f9a7b6c5d4e",
                         "chatUuid":"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                         "senderId":4,"senderUuid":"2afcbb98-85bd-4a24-be7d-5e66dbe53933",
                         "senderName":"itest_audit_02","senderAvatar":null,
                         "text":"forwarded","messageType":"TEXT","replyToMessageUuid":null,
                         "isEdited":false,"isDeleted":false,"isPinned":false,
                         "createdAt":"2026-09-20T08:00:00","updatedAt":null}"""
                )
        )

        val result = repository()
            .forwardMessage("3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d")
            .toList()
            .last()

        assertTrue(result is Resource.Success)
        assertEquals("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d", result.data!!.chatUuid)

        val request = server.takeRequest()
        assertEquals("/api/messages/forward", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", body.get("messageUuid").asString)
        assertEquals("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d", body.get("targetChatUuid").asString)
    }

    @Test
    fun `pinMessage hits the pin endpoint`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200))

        val pinned = repository()
            .pinMessage("7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f", "3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33")

        assertTrue(pinned)
        val request = server.takeRequest()
        assertEquals(
            "/api/messages/7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f/pin/3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33",
            request.path
        )
        assertEquals("GET", request.method)
    }

    @Test
    fun `unpinMessage sends DELETE to the pin endpoint`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200))

        val unpinned = repository()
            .unpinMessage("7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f", "3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33")

        assertTrue(unpinned)
        val request = server.takeRequest()
        assertEquals(
            "/api/messages/7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f/pin/3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33",
            request.path
        )
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `loadPinned parses the pinned list`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[$liveLikeMessage]")
        )

        val pinned = repository().loadPinned("7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f")

        assertEquals(1, pinned.size)
        assertEquals("3f9a2c14-77bb-4a1e-9c0f-2a5e8d1b6a33", pinned.single().messageUuid)

        val request = server.takeRequest()
        assertEquals("/api/messages/7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f/pinned", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `loadPinned degrades to an empty list on a server error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(500))

        val pinned = repository().loadPinned("7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f")

        assertTrue(pinned.isEmpty())
        assertNull(pinned.firstOrNull())
    }
}
