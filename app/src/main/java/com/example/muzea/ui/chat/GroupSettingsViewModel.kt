package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class GroupSettingsViewModel(
    private val chatUuid: String,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _participants = MutableSharedFlow<NetworkResult<List<ChatParticipantResponse>>>()
    val participants: SharedFlow<NetworkResult<List<ChatParticipantResponse>>> =
        _participants.asSharedFlow()

    private val _contactsResult = MutableSharedFlow<NetworkResult<List<ContactResponse>>>()
    val contactsResult: SharedFlow<NetworkResult<List<ContactResponse>>> =
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
                _participants.emit(result)
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            chatRepository.loadContacts().collect { result ->
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
                chatRepository.uploadChatAvatar(chatUuid, file, mimeType)
                    .collect { _avatarState.emit(it) }
            } finally {
                file.delete()
                avatarUploadActive = false
            }
        }
    }
}