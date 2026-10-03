package com.example.muzea.domain.usecase

import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.core.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPrivateChatUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val useCase = OpenPrivateChatUseCase(chatRepository)

    private fun chat(uuid: String) = Chat(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = null,
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    @Test
    fun `returns existing chat without creating a new one`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns chat("chat-1")

        val result = useCase("user-1")

        assertTrue(result is Resource.Success)
        assertEquals("chat-1", (result as Resource.Success).data?.chatUuid)
        coVerify(exactly = 0) { chatRepository.createPrivateChat(any()) }
    }

    @Test
    fun `creates chat when none exists`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns null
        coEvery { chatRepository.createPrivateChat("user-1") } returns
            flowOf(Resource.Success(chat("chat-new")))

        val result = useCase("user-1")

        assertTrue(result is Resource.Success)
        assertEquals("chat-new", (result as Resource.Success).data?.chatUuid)
        coVerify(exactly = 1) { chatRepository.createPrivateChat("user-1") }
    }

    @Test
    fun `surfaces create failure`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns null
        coEvery { chatRepository.createPrivateChat("user-1") } returns
            flowOf(Resource.Error("boom"))

        val result = useCase("user-1")

        assertTrue(result is Resource.Error)
        assertEquals("boom", (result as Resource.Error).message)
    }

    @Test
    fun `falls back to create when lookup throws`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } throws RuntimeException("network")
        coEvery { chatRepository.createPrivateChat("user-1") } returns
            flowOf(Resource.Success(chat("chat-new")))

        val result = useCase("user-1")

        assertTrue(result is Resource.Success)
        assertEquals("chat-new", (result as Resource.Success).data?.chatUuid)
    }
}
