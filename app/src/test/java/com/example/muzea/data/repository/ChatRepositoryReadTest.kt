package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ChatResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryReadTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val chatRepository = ChatRepository(api, auth)
    private val messageRepository = MessageRepository(api, auth)

    @Test
    fun `marks messages as read up to the given message`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returns Response.success(Unit)

        assertTrue(messageRepository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 1) { api.markMessagesAsRead("chat-1", "msg-9") }
    }

    @Test
    fun `retries once after expired chat token`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returnsMany listOf(
            Response.error(401, "expired".toResponseBody()),
            Response.success(Unit)
        )

        assertTrue(messageRepository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 2) { api.markMessagesAsRead("chat-1", "msg-9") }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `returns false when not authenticated`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        assertFalse(messageRepository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 0) { api.markMessagesAsRead(any(), any()) }
    }

    @Test
    fun `returns false on server error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returns Response.error(500, "boom".toResponseBody())

        assertFalse(messageRepository.markMessagesAsRead("chat-1", "msg-9"))
    }

    @Test
    fun `fetched chat list lands in the shared cache`() = runTest {
        ChatListCache.clear()
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChats() } returns Response.success(
            listOf(chat("a", "Anna"), chat("b", "Boris"))
        )

        chatRepository.loadChats().collect { }

        // Новый экран создаёт свой репозиторий и полагается на этот кэш,
        // чтобы показать список до ответа сервера.
        assertEquals(listOf("a", "b"), ChatListCache.get().map { it.chatUuid })
        assertEquals(listOf("a", "b"), chatRepository.cachedChats().map { it.chatUuid })
        ChatListCache.clear()
    }

    @Test
    fun `failed refresh keeps previously cached chat list`() = runTest {
        ChatListCache.clear()
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChats() } returnsMany listOf(
            Response.success(listOf(chat("a", "Anna"))),
            Response.error(500, "boom".toResponseBody())
        )

        chatRepository.loadChats().collect { }
        chatRepository.loadChats().collect { }

        assertEquals(listOf("a"), ChatListCache.get().map { it.chatUuid })
        ChatListCache.clear()
    }

    private fun chat(uuid: String, title: String) = ChatResponse(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = title,
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )
}
