package com.example.muzea.ui.chat

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForwardChatPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = mockk<ChatRepository>()

    private fun chat(uuid: String) = Chat(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = "Chat $uuid",
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    @Test
    fun `load exposes chats except the current one`() = runTest {
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Success(listOf(chat("a"), chat("b"), chat("current")))
        )
        val viewModel = ForwardChatPickerViewModel(chatRepository)

        viewModel.load("current")

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("a", "b"), state.chats.map { it.chatUuid })
    }

    @Test
    fun `load filters by null current chat`() = runTest {
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Success(listOf(chat("a")))
        )
        val viewModel = ForwardChatPickerViewModel(chatRepository)

        viewModel.load(null)

        assertEquals(listOf("a"), viewModel.state.value.chats.map { it.chatUuid })
    }

    @Test
    fun `load is idempotent and only requests once`() = runTest {
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = ForwardChatPickerViewModel(chatRepository)

        viewModel.load("current")
        viewModel.load("current")

        coVerify(exactly = 1) { chatRepository.loadChats() }
    }

    @Test
    fun `error keeps the list empty and stops loading`() = runTest {
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Error("offline"))
        val viewModel = ForwardChatPickerViewModel(chatRepository)

        viewModel.load("current")

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.chats.isEmpty())
    }

    @Test
    fun `success with an empty list yields no chats`() = runTest {
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = ForwardChatPickerViewModel(chatRepository)

        viewModel.load(null)

        assertTrue(viewModel.state.value.chats.isEmpty())
    }
}
