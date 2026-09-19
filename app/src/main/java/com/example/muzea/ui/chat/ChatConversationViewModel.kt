package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatConversationViewModel(
    private val chatUuid: String,
    private val chatRepository: ChatRepository,
    val myUserUuid: String?
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messages: StateFlow<List<MessageResponse>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sendError = MutableSharedFlow<String>()
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    private var pollingJob: Job? = null

    init {
        refresh()
        startPolling()
    }

    fun refresh() {
        viewModelScope.launch {
            chatRepository.loadMessages(chatUuid).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> _isLoading.value = true
                    is NetworkResult.Success -> {
                        _isLoading.value = false
                        mergeMessages(result.data ?: emptyList())
                    }
                    is NetworkResult.Error -> {
                        _isLoading.value = false
                        _error.value = result.message
                    }
                }
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val optimistic = MessageResponse(
            messageUuid = "local-" + System.currentTimeMillis(),
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = trimmed,
            messageType = "TEXT",
            replyToMessageUuid = null,
            isEdited = false,
            isDeleted = false,
            isPinned = false,
            createdAt = null,
            updatedAt = null
        )
        mergeMessages(listOf(optimistic))

        viewModelScope.launch {
            chatRepository.sendMessage(chatUuid, trimmed).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        removeLocalMessage(optimistic.messageUuid)
                        val sent = result.data!!
                        mergeMessages(listOf(sent))
                    }
                    is NetworkResult.Error -> {
                        _sendError.emit(result.message ?: "Failed to send message")
                    }
                    is NetworkResult.Loading -> {}
                }
            }
        }
    }

    private fun removeLocalMessage(localUuid: String) {
        _messages.value = _messages.value.filter { it.messageUuid != localUuid }
    }

    private fun mergeMessages(incoming: List<MessageResponse>) {
        val merged = LinkedHashMap<String, MessageResponse>()
        for (m in incoming) merged[m.messageUuid] = m
        for (m in _messages.value) merged[m.messageUuid] = m
        _messages.value = merged.values.toList().sortedWith(MessageComparator())
    }

    private class MessageComparator : Comparator<MessageResponse> {
        override fun compare(a: MessageResponse, b: MessageResponse): Int {
            val ta = a.createdAt ?: ""
            val tb = b.createdAt ?: ""
            if (ta.isEmpty() && tb.isEmpty()) return 0
            if (ta.isEmpty()) return 1
            if (tb.isEmpty()) return -1
            return ta.compareTo(tb)
        }
    }

    private fun startPolling() {
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                chatRepository.loadMessages(chatUuid).collect { result ->
                    if (result is NetworkResult.Success) {
                        mergeMessages(result.data ?: emptyList())
                    }
                }
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}