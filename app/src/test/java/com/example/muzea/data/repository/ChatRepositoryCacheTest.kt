package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.domain.model.Chat
import com.example.muzea.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Кэширование на стороне репозитория: список чатов, сообщения и поиск
 * существующего приватного чата. Все объекты-кэши — синглтоны, поэтому их
 * состояние обязательно сбрасывается до и после каждого теста.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryCacheTest {

    @get:Rule
    val server = MockWebServer()

    private val auth = io.mockk.mockk<ChatAuthManager>(relaxed = true)

    private fun chatRepo() = ChatRepositoryImpl(IntegrationTestClient.chatApi(server), auth)

    private fun messageRepo() = MessageRepositoryImpl(IntegrationTestClient.chatApi(server), auth)

    @Before
    fun setUp() {
        ChatListCache.clear()
        ChatMessagesCache.clear()
        PrivateChatCache.clear()
        coEvery { auth.isAuthenticated() } returns true
    }

    @After
    fun tearDown() {
        ChatListCache.clear()
        ChatMessagesCache.clear()
        PrivateChatCache.clear()
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private val privateChat = """
        {"chatUuid":"chat-1","chatType":"PRIVATE","title":null,"avatarUrl":null,
         "createdAt":null,"updatedAt":null,"participantCount":2,
         "lastMessage":null,"unreadCount":0}
    """.trimIndent()

    private val groupChat = """
        {"chatUuid":"group-1","chatType":"GROUP","title":"Team","avatarUrl":null,
         "createdAt":null,"updatedAt":null,"participantCount":3,
         "lastMessage":null,"unreadCount":0}
    """.trimIndent()

    private val participants = """
        [{"userUuid":"me","username":"me","role":"MEMBER","online":true},
         {"userUuid":"user-1","username":"u1","role":"MEMBER","online":true}]
    """.trimIndent()

    @Test
    fun `loadChats stores the list in the cache`() = runTest {
        server.enqueue(json("[$privateChat]"))

        val result = chatRepo().loadChats().toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(listOf("chat-1"), ChatListCache.get().map { it.chatUuid })
    }

    @Test
    fun `loadMessages stores messages and exposes them via cachedMessages`() = runTest {
        server.enqueue(
            json(
                """{"content":[{"messageUuid":"m1","chatUuid":"chat-1","senderUuid":"user-1",
                     "text":"hi","messageType":"TEXT","isEdited":false,"isDeleted":false,
                     "isPinned":false,"createdAt":"2026-01-01T00:00:00"}],
                     "empty":false,"first":true,"last":true,"number":0,
                     "numberOfElements":1,"size":50,"totalElements":1,"totalPages":1}"""
            )
        )

        val result = messageRepo().loadMessages("chat-1").toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals(listOf("m1"), ChatMessagesCache.get("chat-1").map { it.messageUuid })
        assertEquals(listOf("m1"), messageRepo().cachedMessages("chat-1").map { it.messageUuid })
    }

    @Test
    fun `findPrivateChatWith returns cached chat without touching the network`() = runTest {
        PrivateChatCache.put("user-1", chat("chat-1"))

        val found = chatRepo().findPrivateChatWith("user-1")

        assertEquals("chat-1", found?.chatUuid)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `findPrivateChatWith scans chats and participants and fills the cache`() = runTest {
        server.enqueue(json("[$privateChat]"))
        server.enqueue(json(participants))

        val found = chatRepo().findPrivateChatWith("user-1")

        assertEquals("chat-1", found?.chatUuid)
        // Кэш заполняется по всем участникам найденного приватного чата.
        assertEquals("chat-1", PrivateChatCache.get("user-1")?.chatUuid)
        assertEquals("chat-1", PrivateChatCache.get("me")?.chatUuid)

        // Повторный поиск (в т.ч. по другому участнику) уже идёт из кэша.
        val again = chatRepo().findPrivateChatWith("me")
        assertEquals("chat-1", again?.chatUuid)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `findPrivateChatWith returns null when there is no matching private chat`() = runTest {
        server.enqueue(json("[$privateChat]"))
        server.enqueue(json("""[{"userUuid":"stranger","online":false}]"""))

        assertNull(chatRepo().findPrivateChatWith("user-1"))
    }

    @Test
    fun `findPrivateChatWith skips group chats without requesting their participants`() = runTest {
        server.enqueue(json("[$groupChat]"))

        assertNull(chatRepo().findPrivateChatWith("user-1"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `createPrivateChat caches the created chat`() = runTest {
        server.enqueue(json(privateChatJson("chat-new")))

        val result = chatRepo().createPrivateChat("user-9").toList().last()

        assertTrue(result is NetworkResult.Success)
        assertEquals("chat-new", PrivateChatCache.get("user-9")?.chatUuid)
        coVerify(exactly = 0) { auth.invalidate() }
    }

    private fun chat(chatUuid: String) = Chat(
        chatUuid = chatUuid,
        chatType = "PRIVATE",
        title = null,
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    private fun privateChatJson(chatUuid: String) =
        """{"chatUuid":"$chatUuid","chatType":"PRIVATE","title":null,"avatarUrl":null,
            "createdAt":null,"updatedAt":null,"participantCount":2,
            "lastMessage":null,"unreadCount":0}""".trimIndent()
}
