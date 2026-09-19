package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryReadTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ChatRepository(api, auth)

    @Test
    fun `marks messages as read up to the given message`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returns Response.success(Unit)

        assertTrue(repository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 1) { api.markMessagesAsRead("chat-1", "msg-9") }
    }

    @Test
    fun `retries once after expired chat token`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returnsMany listOf(
            Response.error(401, "expired".toResponseBody()),
            Response.success(Unit)
        )

        assertTrue(repository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 2) { api.markMessagesAsRead("chat-1", "msg-9") }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `returns false when not authenticated`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        assertFalse(repository.markMessagesAsRead("chat-1", "msg-9"))

        coVerify(exactly = 0) { api.markMessagesAsRead(any(), any()) }
    }

    @Test
    fun `returns false on server error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.markMessagesAsRead("chat-1", "msg-9") } returns Response.error(500, "boom".toResponseBody())

        assertFalse(repository.markMessagesAsRead("chat-1", "msg-9"))
    }
}
