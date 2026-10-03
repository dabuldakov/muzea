package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.example.muzea.core.Resource
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = mockk<ChatRepository>()
    private val contactRepository = mockk<ContactRepository>()
    private val avatarRepository = mockk<AvatarRepository>()
    private val identity = object : ChatUserIdentity {
        override val userUuid: String? = "me"
    }

    private fun viewModel(): GroupSettingsViewModel = GroupSettingsViewModel(
        SavedStateHandle(mapOf(GroupSettingsViewModel.ARG_CHAT_UUID to "chat-1")),
        chatRepository,
        contactRepository,
        avatarRepository,
        identity
    )

    private fun participant(uuid: String) = ChatParticipant(
        userUuid = uuid,
        userId = null,
        username = uuid,
        firstName = null,
        lastName = null,
        fullName = null,
        nickname = null,
        avatarUrl = null,
        role = "MEMBER",
        online = false,
        lastSeenAt = null,
        joinedAt = null
    )

    @Test
    fun `participants are exposed in state`() = runTest {
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns flowOf(
            Resource.Success(listOf(participant("a"), participant("b")))
        )

        val viewModel = viewModel()
        viewModel.loadParticipants()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.participants.map { it.userUuid })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `load error is exposed and consumable`() = runTest {
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns flowOf(
            Resource.Error("boom")
        )

        val viewModel = viewModel()
        viewModel.loadParticipants()
        assertEquals("boom", viewModel.uiState.value.error)

        viewModel.consumeError()
        assertNull(viewModel.uiState.value.error)
    }
}
