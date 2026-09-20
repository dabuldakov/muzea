package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.NetworkResult
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Интеграционные тесты чата против MockWebServer: добавление контакта,
 * создание приватного чата, чтение списка контактов и загрузка аватара.
 * Закрепляют контракты живого сервера (пути, JSON-поля запросов/ответов).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private val auth = mockk<ChatAuthManager>(relaxed = true)

    private fun repo() = ChatRepository(IntegrationTestClient.chatApi(server), auth)

    private val liveLikeContact = """
        {"contactUuid":"d040b46f-891d-4823-992f-384cfe5af824",
         "contactUserId":4,
         "contactUserUuid":"2afcbb98-85bd-4a24-be7d-5e66dbe53933",
         "username":"itest_audit_02","firstName":null,"lastName":null,
         "fullName":"itest_audit_02","avatarUrl":null,
         "contactName":"itest_audit_02","isOnline":false,
         "lastSeenAt":null,"addedAt":"2026-09-20T07:03:22"}
    """.trimIndent()

    private val liveLikeChat = """
        {"chatUuid":"7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f",
         "chatType":"PRIVATE","title":null,"avatarUrl":null,
         "createdAt":"2026-09-20T07:03:22","updatedAt":"2026-09-20T07:03:22",
         "participantCount":2,"lastMessage":null,"unreadCount":0}
    """.trimIndent()

    @Test
    fun `addContactByUsername looks up user then posts add-contact body`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"userUuid":"2afcbb98-85bd-4a24-be7d-5e66dbe53933","username":"itest_audit_02"}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeContact)
        )

        val result = repo().addContactByUsername("itest_audit_02").toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(
            "2afcbb98-85bd-4a24-be7d-5e66dbe53933",
            result.data!!.contactUserUuid
        )

        val lookup = server.takeRequest()
        assertEquals("/api/users/by-username/itest_audit_02", lookup.path)
        assertEquals("GET", lookup.method)

        val add = server.takeRequest()
        assertEquals("/api/contacts", add.path)
        assertEquals("POST", add.method)
        val body = JsonParser().parse(add.body.readUtf8()).asJsonObject
        assertEquals("2afcbb98-85bd-4a24-be7d-5e66dbe53933", body.get("contactUserUuid").asString)
        // Gson по умолчанию не сериализует null-поля — сервер принимает
        // запрос и без contactName (проверено на живом сервере).
        assertNull(body.get("contactName"))
    }

    @Test
    fun `addContactByUsername reports missing user`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(404))

        val result = repo().addContactByUsername("no-such-user").toList().last()

        assertTrue(result is NetworkResult.Error)
        assertEquals("User not found: no-such-user", (result as NetworkResult.Error).message)
    }

    @Test
    fun `createPrivateChat posts otherUserUuid body`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeChat)
        )

        val result = repo().createPrivateChat("2afcbb98-85bd-4a24-be7d-5e66dbe53933").toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals("7b0b15f8-6b3c-4c2d-a9e2-2e1e32de9b1f", result.data!!.chatUuid)

        val request = server.takeRequest()
        assertEquals("/api/chats/private", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("2afcbb98-85bd-4a24-be7d-5e66dbe53933", body.get("otherUserUuid").asString)
    }

    @Test
    fun `loadContacts parses contactUserUuid from list`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[$liveLikeContact]")
        )

        val result = repo().loadContacts().toList().last()

        assertTrue(result is NetworkResult.Success)
        val contact = result.data!!.single()
        assertEquals("2afcbb98-85bd-4a24-be7d-5e66dbe53933", contact.contactUserUuid)
        assertEquals("itest_audit_02", contact.username)
        assertEquals("itest_audit_02", contact.displayName())

        val request = server.takeRequest()
        assertEquals("/api/contacts", request.path)
    }

    @Test
    fun `loadAvatar resolves relative avatarUrl via MediaUrl chat base`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"userUuid":"f8ea45c4-6e3c-4b85-8534-c3f3f08d079a","username":"me",
                         "avatarUrl":"/api/avatars/f8ea45c4-6e3c-4b85-8534-c3f3f08d079a/6dccd85f-f355-4b93-bc9a-248c09c917f2.png"}"""
                )
        )

        val result = repo().loadAvatar().toList().last()

        assertTrue(result is NetworkResult.Success)
        val avatarPath = result.data!!.avatarUrl
        assertEquals(
            "/api/avatars/f8ea45c4-6e3c-4b85-8534-c3f3f08d079a/6dccd85f-f355-4b93-bc9a-248c09c917f2.png",
            avatarPath
        )
        assertEquals(
            "http://90.188.89.63:8086/api/avatars/f8ea45c4-6e3c-4b85-8534-c3f3f08d079a/6dccd85f-f355-4b93-bc9a-248c09c917f2.png",
            MediaUrl.chat(avatarPath)
        )

        val request = server.takeRequest()
        assertEquals("/api/users/me", request.path)
    }

    @Test
    fun `loadContacts surfaces the chat auth failure reason`() = runTest {
        coEvery { auth.isAuthenticated() } returns false
        every { auth.lastFailureMessage } returns "Chat account \"xoxo\" already exists on the chat server"

        val result = repo().loadContacts().toList().last()

        assertTrue(result is NetworkResult.Error)
        assertEquals(
            "Chat auth failed. Chat account \"xoxo\" already exists on the chat server",
            (result as NetworkResult.Error).message
        )
        assertEquals(0, server.requestCount)
    }
}