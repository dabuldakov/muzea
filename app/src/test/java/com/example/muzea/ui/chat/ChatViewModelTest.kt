package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = mockk<ChatRepository>()
    private val contactRepository = mockk<ContactRepository>()

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

    @Before
    fun setUp() {
        // Репозиторий здесь — мок, поэтому состояние берётся только из
        // заглушек cachedChats() и общий кэш списка не участвует.
    }

    @After
    fun tearDown() = Unit

    @Test
    fun `cached chats are visible immediately without spinner`() {
        every { chatRepository.cachedChats() } returns listOf(chat("a"), chat("b"))

        val viewModel = ChatViewModel(chatRepository, contactRepository)

        assertEquals(listOf("a", "b"), viewModel.uiState.value.chats.map { it.chatUuid })
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { chatRepository.loadChats() }
    }

    @Test
    fun `spinner shows only when there is nothing to display`() {
        every { chatRepository.cachedChats() } returns emptyList()

        val viewModel = ChatViewModel(chatRepository, contactRepository)

        assertTrue(viewModel.uiState.value.chats.isEmpty())
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `network result replaces cached chats and hides spinner`() = runTest {
        every { chatRepository.cachedChats() } returns listOf(chat("stale"))
        coEvery { chatRepository.loadChats() } returns flowOf(
            NetworkResult.Success(listOf(chat("fresh-1"), chat("fresh-2")))
        )

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()

        assertEquals(listOf("fresh-1", "fresh-2"), viewModel.uiState.value.chats.map { it.chatUuid })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `late collector receives current list without new network request`() = runTest {
        every { chatRepository.cachedChats() } returns listOf(chat("cached"))
        coEvery { chatRepository.loadChats() } returns flowOf(
            NetworkResult.Success(listOf(chat("fresh")))
        )

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()

        // Подписчик появляется уже после загрузки — StateFlow обязан отдать значение.
        val seen = viewModel.uiState.value.chats
        assertEquals(listOf("fresh"), seen.map { it.chatUuid })
        coVerify(exactly = 1) { chatRepository.loadChats() }
    }

    @Test
    fun `load in progress is not restarted by another call`() = runTest {
        every { chatRepository.cachedChats() } returns emptyList()
        // Первый запрос «висит», как реальный сетевой вызов.
        coEvery { chatRepository.loadChats() } returns flow {
            emit(NetworkResult.Loading())
            awaitCancellation()
        }

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        // Экран дёргает загрузку из onResume и из автообновления раз в 8 секунд.
        viewModel.loadChats()
        viewModel.loadChats()
        viewModel.loadChats()

        // Пока предыдущий запрос не завершён, новый не должен уходить на сервер.
        coVerify(exactly = 1) { chatRepository.loadChats() }
    }

    @Test
    fun `sequential loads are allowed once the previous one finished`() = runTest {
        every { chatRepository.cachedChats() } returns emptyList()
        coEvery { chatRepository.loadChats() } returns flowOf(
            NetworkResult.Success(listOf(chat("a")))
        )

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()
        viewModel.loadChats()

        // Защита от наложения не должна блокировать обновление навсегда.
        coVerify(exactly = 2) { chatRepository.loadChats() }
    }

    @Test
    fun `network failure keeps cached chats on screen`() = runTest {
        every { chatRepository.cachedChats() } returns listOf(chat("cached"))
        coEvery { chatRepository.loadChats() } returns flowOf(
            NetworkResult.Error("network down")
        )

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()

        assertEquals(listOf("cached"), viewModel.uiState.value.chats.map { it.chatUuid })
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.error!!.contains("network down"))
    }

    @Test
    fun `network failure hides spinner when there is nothing cached`() = runTest {
        every { chatRepository.cachedChats() } returns emptyList()
        coEvery { chatRepository.loadChats() } returns flowOf(
            NetworkResult.Error("network down")
        )

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("network down", viewModel.uiState.value.error)
    }

    @Test
    fun `empty successful response clears cached chats`() = runTest {
        every { chatRepository.cachedChats() } returns listOf(chat("cached"))
        coEvery { chatRepository.loadChats() } returns flowOf(NetworkResult.Success(emptyList()))

        val viewModel = ChatViewModel(chatRepository, contactRepository)
        viewModel.loadChats()

        assertTrue(viewModel.uiState.value.chats.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

}