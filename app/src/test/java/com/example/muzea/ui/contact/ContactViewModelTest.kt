package com.example.muzea.ui.contact

import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Presence
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.usecase.OpenPrivateChatUseCase
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = mockk<ChatRepository>()
    private val contactRepository = mockk<ContactRepository>()

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

    private fun CoroutineScope.collectResults(
        viewModel: ContactViewModel,
        block: () -> Unit
    ): List<NetworkResult<Chat>> {
        val results = mutableListOf<NetworkResult<Chat>>()
        // Подписка до действия: createChatResult — одноразовый SharedFlow.
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.createChatResult.collect { results += it }
        }
        block()
        collector.cancel()
        return results
    }

    @Test
    fun `openPrivateChat reuses an existing chat without creating a new one`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns chat("chat-1")
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        val results = collectResults(viewModel) { viewModel.openPrivateChat("user-1") }

        assertTrue(results.first() is NetworkResult.Loading)
        assertEquals("chat-1", (results.last() as NetworkResult.Success).data?.chatUuid)
        coVerify(exactly = 0) { chatRepository.createPrivateChat(any()) }
    }

    @Test
    fun `openPrivateChat creates a chat when none exists`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns null
        coEvery { chatRepository.createPrivateChat("user-1") } returns
            flowOf(NetworkResult.Success(chat("chat-new")))
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        val results = collectResults(viewModel) { viewModel.openPrivateChat("user-1") }

        assertEquals("chat-new", (results.last() as NetworkResult.Success).data?.chatUuid)
        coVerify(exactly = 1) { chatRepository.createPrivateChat("user-1") }
    }

    @Test
    fun `openPrivateChat surfaces create failure`() = runTest {
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns null
        coEvery { chatRepository.createPrivateChat("user-1") } returns
            flowOf(NetworkResult.Error("boom"))
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        val results = collectResults(viewModel) { viewModel.openPrivateChat("user-1") }

        assertTrue(results.last() is NetworkResult.Error)
        assertEquals("boom", (results.last() as NetworkResult.Error).message)
    }

    private fun contact(uuid: String, online: Boolean = false) = Contact(
        contactUuid = "c-$uuid",
        contactUserId = null,
        contactUserUuid = uuid,
        username = uuid,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = null,
        contactName = uuid,
        isOnline = online,
        lastSeenAt = null,
        addedAt = null
    )

    @Test
    fun `loadContacts exposes contacts in state`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            NetworkResult.Success(listOf(contact("a"), contact("b")))
        )
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        viewModel.loadContacts()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.contacts.map { it.contactUserUuid })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadContacts exposes error`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(NetworkResult.Error("boom"))
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        viewModel.loadContacts()
        assertEquals("boom", viewModel.uiState.value.error)

        viewModel.consumeError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `refreshPresence updates online flag`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            NetworkResult.Success(listOf(contact("a")))
        )
        coEvery { contactRepository.loadPresence(listOf("a")) } returns
            mapOf("a" to Presence(online = true, lastSeenAt = "2026-01-01T00:00:00"))
        val viewModel = ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

        viewModel.loadContacts()
        viewModel.refreshPresence()

        assertTrue(viewModel.uiState.value.contacts.single().isOnline)
    }
}
