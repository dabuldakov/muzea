package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _chatsResult = MutableSharedFlow<NetworkResult<List<ChatResponse>>>()
    val chatsResult: SharedFlow<NetworkResult<List<ChatResponse>>> = _chatsResult.asSharedFlow()

    private val _contactsResult = MutableSharedFlow<NetworkResult<List<ContactResponse>>>()
    val contactsResult: SharedFlow<NetworkResult<List<ContactResponse>>> = _contactsResult.asSharedFlow()

    private val _createGroupChatResult = MutableSharedFlow<NetworkResult<ChatResponse>>()
    val createGroupChatResult: SharedFlow<NetworkResult<ChatResponse>> =
        _createGroupChatResult.asSharedFlow()

    private val _addParticipantsResult = MutableSharedFlow<NetworkResult<Unit>>()
    val addParticipantsResult: SharedFlow<NetworkResult<Unit>> =
        _addParticipantsResult.asSharedFlow()

    fun loadChats() {
        viewModelScope.launch {
            chatRepository.loadChats().collect { result ->
                _chatsResult.emit(result)
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

    fun createGroupChat(title: String, memberUuids: List<String>) {
        viewModelScope.launch {
            chatRepository.createGroupChat(title, memberUuids).collect { result ->
                _createGroupChatResult.emit(result)
            }
        }
    }

    fun addGroupParticipants(chatUuid: String, memberUuids: List<String>) {
        viewModelScope.launch {
            chatRepository.addGroupParticipants(chatUuid, memberUuids).collect { result ->
                _addParticipantsResult.emit(result)
            }
        }
    }
}