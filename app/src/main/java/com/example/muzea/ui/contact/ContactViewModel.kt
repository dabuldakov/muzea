package com.example.muzea.ui.contact

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

class ContactViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _contactsResult = MutableSharedFlow<NetworkResult<List<ContactResponse>>>()
    val contactsResult: SharedFlow<NetworkResult<List<ContactResponse>>> = _contactsResult.asSharedFlow()

    private val _addContactResult = MutableSharedFlow<NetworkResult<ContactResponse>>()
    val addContactResult: SharedFlow<NetworkResult<ContactResponse>> = _addContactResult.asSharedFlow()

    private val _createChatResult = MutableSharedFlow<NetworkResult<ChatResponse>>()
    val createChatResult: SharedFlow<NetworkResult<ChatResponse>> = _createChatResult.asSharedFlow()

    fun loadContacts() {
        viewModelScope.launch {
            chatRepository.loadContacts().collect { result ->
                _contactsResult.emit(result)
            }
        }
    }

    fun addContact(username: String) {
        viewModelScope.launch {
            chatRepository.addContactByUsername(username).collect { result ->
                _addContactResult.emit(result)
            }
        }
    }

    fun createPrivateChat(userUuid: String) {
        viewModelScope.launch {
            chatRepository.createPrivateChat(userUuid).collect { result ->
                _createChatResult.emit(result)
            }
        }
    }
}