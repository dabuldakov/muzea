package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.ChatResponse
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

    fun loadChats() {
        viewModelScope.launch {
            chatRepository.loadChats().collect { result ->
                _chatsResult.emit(result)
            }
        }
    }
}