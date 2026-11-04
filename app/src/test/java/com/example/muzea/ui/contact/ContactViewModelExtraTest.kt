package com.example.muzea.ui.contact

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Presence
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.usecase.OpenPrivateChatUseCase
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Дополнительные тесты ContactViewModel: состояние загрузки, защита от
 * наложения запросов, добавление контакта и тонкости refreshPresence
 * (пустой ответ, отсутствие lastSeen, ошибки сети, контакты без uuid).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = mockk<ChatRepository>()
    private val contactRepository = mockk<ContactRepository>()

    private fun viewModel() =
        ContactViewModel(contactRepository, OpenPrivateChatUseCase(chatRepository))

    private fun contact(uuid: String?, lastSeenAt: String? = null) = Contact(
        contactUuid = "c-$uuid",
        contactUserId = 1L,
        contactUserUuid = uuid,
        username = uuid,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = null,
        contactName = uuid,
        isOnline = false,
        lastSeenAt = lastSeenAt,
        addedAt = null
    )

    private fun CoroutineScope.collectAddResults(
        viewModel: ContactViewModel,
        block: () -> Unit
    ): List<Resource<Contact>> {
        val results = mutableListOf<Resource<Contact>>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.addContactResult.collect { results += it }
        }
        block()
        collector.cancel()
        return results
    }

    @Test
    fun `loadContacts shows loading and ignores a concurrent call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { contactRepository.loadContacts() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(listOf(contact("a"))))
        }
        val viewModel = viewModel()

        viewModel.loadContacts()
        assertTrue(viewModel.uiState.value.isLoading)

        // Второй вызов, пока первый активен, не должен бить по сети.
        viewModel.loadContacts()
        coVerify(exactly = 1) { contactRepository.loadContacts() }

        gate.complete(Unit)
        assertEquals(listOf("a"), viewModel.uiState.value.contacts.map { it.contactUserUuid })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `addContact forwards loading and success`() = runTest {
        coEvery { contactRepository.addContactByUsername("bob") } returns flowOf(
            Resource.Loading(),
            Resource.Success(contact("bob"))
        )
        val viewModel = viewModel()

        val results = collectAddResults(viewModel) { viewModel.addContact("bob") }

        assertTrue(results.first() is Resource.Loading)
        assertEquals("bob", (results.last() as Resource.Success).data?.contactUserUuid)
        coVerify(exactly = 1) { contactRepository.addContactByUsername("bob") }
    }

    @Test
    fun `refreshPresence does nothing when there are no contacts`() = runTest {
        val viewModel = viewModel()

        viewModel.refreshPresence()

        coVerify(exactly = 0) { contactRepository.loadPresence(any()) }
    }

    @Test
    fun `refreshPresence ignores contacts without a user uuid`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact(uuid = null)))
        )
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        coVerify(exactly = 0) { contactRepository.loadPresence(any()) }
    }

    @Test
    fun `refreshPresence keeps contacts when the server returns nothing`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("a")))
        )
        coEvery { contactRepository.loadPresence(listOf("a")) } returns emptyMap()
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        val current = viewModel.uiState.value.contacts.single()
        assertFalse(current.isOnline)
        assertEquals(null, current.lastSeenAt)
    }

    @Test
    fun `refreshPresence keeps the previous lastSeen when the server omits it`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("a", lastSeenAt = "2026-01-01T00:00:00")))
        )
        coEvery { contactRepository.loadPresence(listOf("a")) } returns
            mapOf("a" to Presence(online = true, lastSeenAt = null))
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        val current = viewModel.uiState.value.contacts.single()
        assertTrue(current.isOnline)
        assertEquals("2026-01-01T00:00:00", current.lastSeenAt)
    }

    @Test
    fun `refreshPresence swallows a network failure`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("a")))
        )
        coEvery { contactRepository.loadPresence(listOf("a")) } throws RuntimeException("offline")
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        // Список не пострадал, статусы остались прежними.
        assertEquals(listOf("a"), viewModel.uiState.value.contacts.map { it.contactUserUuid })
        assertFalse(viewModel.uiState.value.contacts.single().isOnline)
    }

    @Test
    fun `refreshPresence leaves contacts missing from the response untouched`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(
                listOf(contact("a"), contact("b", lastSeenAt = "2026-01-01T00:00:00"))
            )
        )
        coEvery { contactRepository.loadPresence(listOf("a", "b")) } returns
            mapOf("a" to Presence(online = true, lastSeenAt = null))
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        val untouched = viewModel.uiState.value.contacts.first { it.contactUserUuid == "b" }
        assertFalse(untouched.isOnline)
        assertEquals("2026-01-01T00:00:00", untouched.lastSeenAt)
    }

    @Test
    fun `refreshPresence propagates cancellation`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("a")))
        )
        coEvery { contactRepository.loadPresence(listOf("a")) } throws
            CancellationException("stop")
        val viewModel = viewModel()
        viewModel.loadContacts()

        viewModel.refreshPresence()

        // Отмена пробрасывается, состояние остаётся прежним.
        assertEquals(listOf("a"), viewModel.uiState.value.contacts.map { it.contactUserUuid })
    }
}
