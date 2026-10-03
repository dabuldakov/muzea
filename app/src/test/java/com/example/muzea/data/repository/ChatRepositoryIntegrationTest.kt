package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.MediaUrl
import com.example.muzea.core.Resource
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
import java.io.File

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

    private fun chatRepo() = ChatRepositoryImpl(IntegrationTestClient.chatApi(server), auth)

    private fun contactRepo() = ContactRepositoryImpl(IntegrationTestClient.chatApi(server), auth)

    private fun avatarRepo() = AvatarRepositoryImpl(IntegrationTestClient.chatApi(server), auth)

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

        val result = contactRepo().addContactByUsername("itest_audit_02").toList().last()

        assertTrue(result is Resource.Success)
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

        val result = contactRepo().addContactByUsername("no-such-user").toList().last()

        assertTrue(result is Resource.Error)
        assertEquals("User not found: no-such-user", (result as Resource.Error).message)
    }

    @Test
    fun `createPrivateChat posts otherUserUuid body`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(liveLikeChat)
        )

        val result = chatRepo().createPrivateChat("2afcbb98-85bd-4a24-be7d-5e66dbe53933").toList().last()

        assertTrue(result is Resource.Success)
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

        val result = contactRepo().loadContacts().toList().last()

        assertTrue(result is Resource.Success)
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

        val result = avatarRepo().loadAvatar().toList().last()

        assertTrue(result is Resource.Success)
        val avatarPath = result.data!!.avatarUrl
        assertEquals(
            "/api/avatars/f8ea45c4-6e3c-4b85-8534-c3f3f08d079a/6dccd85f-f355-4b93-bc9a-248c09c917f2.png",
            avatarPath
        )
        assertEquals(
            "https://chat-muzea.su/api/avatars/f8ea45c4-6e3c-4b85-8534-c3f3f08d079a/6dccd85f-f355-4b93-bc9a-248c09c917f2.png",
            MediaUrl.chat(avatarPath)
        )

        val request = server.takeRequest()
        assertEquals("/api/users/me", request.path)
    }

    @Test
    fun `createGroupChat posts title and memberUuids body`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"chatUuid":"9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07",
                         "chatType":"GROUP","title":"Team Talks","avatarUrl":null,
                         "participantCount":1,"lastMessage":null,"unreadCount":0}"""
                )
        )

        val result = chatRepo().createGroupChat("Team Talks", emptyList()).toList().last()

        assertTrue(result is Resource.Success)
        assertEquals("9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07", result.data!!.chatUuid)
        assertEquals("GROUP", result.data!!.chatType)

        val request = server.takeRequest()
        assertEquals("/api/chats/group", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("Team Talks", body.get("title").asString)
        // Gson сериализует пустой список как [] — сервер принимает пустую группу.
        assertTrue(body.get("memberUuids").asJsonArray.size() == 0)
    }

    @Test
    fun `addGroupParticipants posts memberUuids to chat participants endpoint`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200))

        val result = chatRepo()
            .addGroupParticipants(
                "9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07",
                listOf("2afcbb98-85bd-4a24-be7d-5e66dbe53933")
            )
            .toList()
            .last()

        assertTrue(result is Resource.Success)

        val request = server.takeRequest()
        assertEquals("/api/chats/9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07/participants", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        val uuids = body.get("memberUuids").asJsonArray
        assertEquals(1, uuids.size())
        assertEquals("2afcbb98-85bd-4a24-be7d-5e66dbe53933", uuids.get(0).asString)
    }

    @Test
    fun `uploadChatAvatar posts multipart file and parses returned path`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/plain;charset=UTF-8")
                .setBody("chat_avatars/chat_avatar_6_1789894857540.png")
        )

        val file = File.createTempFile("chat_avatar_", ".png")
        file.writeBytes(byteArrayOf(0x89.toByte()))

        val result = avatarRepo()
            .uploadChatAvatar("9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07", file, "image/png")
            .toList()
            .last()
        file.delete()

        assertTrue(
            "expected Success but got ${result} " +
                "message=${(result as? Resource.Error)?.message}",
            result is Resource.Success
        )
        assertEquals("chat_avatars/chat_avatar_6_1789894857540.png", result.data)

        val request = server.takeRequest()
        assertEquals("/api/chats/9f3e1a02-2c7b-4c81-a5e9-3f4d1c2b8a07/avatar", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.contains("multipart/form-data"))
    }

    @Test
    fun `loadChatParticipants parses role and userUuid`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"userUuid":"98099dc4-f7b5-4a74-8344-cc8aa99c5b1a","username":"dabuldakov",
                         "fullName":"dabuldakov","avatarUrl":"/api/avatars/98099dc4-f7b5-4a74-8344-cc8aa99c5b1a/336224d2-034a-44cd-b16b-df0ce834ae7e.png",
                         "role":"OWNER","online":true,"joinedAt":"2026-09-20T08:57:27"},
                        {"userUuid":"0767d93f-4f6c-437c-88fa-a761d83aaf20","username":"oleg",
                         "fullName":"oleg","avatarUrl":null,"role":"MEMBER","online":true,
                         "joinedAt":"2026-09-20T08:57:52"}]"""
                )
        )

        val result = chatRepo()
            .loadChatParticipants("2131d822-1188-4a24-936a-1ad7c9c0cac0")
            .toList()
            .last()

        assertTrue(result is Resource.Success)
        val participants = result.data!!
        assertEquals(2, participants.size)
        assertEquals("dabuldakov", participants[0].displayName())
        assertEquals("OWNER", participants[0].role)
        assertEquals("0767d93f-4f6c-437c-88fa-a761d83aaf20", participants[1].userUuid)

        val request = server.takeRequest()
        assertEquals("/api/chats/2131d822-1188-4a24-936a-1ad7c9c0cac0/participants", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `loadContacts surfaces the chat auth failure reason`() = runTest {
        coEvery { auth.isAuthenticated() } returns false
        every { auth.lastFailureMessage } returns "Chat account \"xoxo\" already exists on the chat server"

        val result = contactRepo().loadContacts().toList().last()

        assertTrue(result is Resource.Error)
        assertEquals(
            "Chat auth failed. Chat account \"xoxo\" already exists on the chat server",
            (result as Resource.Error).message
        )
        assertEquals(0, server.requestCount)
    }
}