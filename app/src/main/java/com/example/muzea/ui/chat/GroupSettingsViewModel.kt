package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Единое состояние экрана настроек группы (участники). */
data class GroupSettingsUiState(
    val participants: List<ChatParticipant> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val avatarRepository: AvatarRepository,
    chatUserIdentity: ChatUserIdentity
) : ViewModel() {

    private val chatUuid: String = savedStateHandle.get<String>(ARG_CHAT_UUID).orEmpty()
    val myUserUuid: String? = chatUserIdentity.userUuid

    private val _uiState = MutableStateFlow(GroupSettingsUiState())
    val uiState: StateFlow<GroupSettingsUiState> = _uiState.asStateFlow()

    private val _contactsResult = MutableSharedFlow<NetworkResult<List<Contact>>>()
    val contactsResult: SharedFlow<NetworkResult<List<Contact>>> =
        _contactsResult.asSharedFlow()

    private val _addParticipantsResult = MutableSharedFlow<NetworkResult<Unit>>()
    val addParticipantsResult: SharedFlow<NetworkResult<Unit>> =
        _addParticipantsResult.asSharedFlow()

    private val _avatarState = MutableSharedFlow<NetworkResult<String>>()
    val avatarState: SharedFlow<NetworkResult<String>> = _avatarState.asSharedFlow()

    private var avatarUploadActive = false

    fun loadParticipants() {
        viewModelScope.launch {
            chatRepository.loadChatParticipants(chatUuid).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.value =
                        _uiState.value.copy(isLoading = _uiState.value.participants.isEmpty())

                    is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                        participants = result.data ?: emptyList(),
                        isLoading = false,
                        error = null
                    )

                    is NetworkResult.Error -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /** Ошибку показали — сбрасываем, чтобы не повторялась. */
    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadContacts() {
        viewModelScope.launch {
            contactRepository.loadContacts().collect { result ->
                _contactsResult.emit(result)
            }
        }
    }

    fun addMembers(memberUuids: List<String>) {
        viewModelScope.launch {
            chatRepository.addGroupParticipants(chatUuid, memberUuids).collect { result ->
                _addParticipantsResult.emit(result)
            }
        }
    }

    fun uploadAvatar(file: File, mimeType: String) {
        if (avatarUploadActive) {
            file.delete()
            return
        }
        avatarUploadActive = true
        viewModelScope.launch {
            try {
                avatarRepository.uploadChatAvatar(chatUuid, file, mimeType)
                    .collect { _avatarState.emit(it) }
            } finally {
                file.delete()
                avatarUploadActive = false
            }
        }
    }

    companion object {
        const val ARG_CHAT_UUID = "chat_uuid"
    }
}