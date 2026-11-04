package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние пикера чата для пересылки. */
data class ForwardPickerUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Список чатов для пересылки сообщения. Текущий чат исключается, чтобы нельзя
 * было «переслать самому себе в тот же чат» случайно.
 */
@HiltViewModel
class ForwardChatPickerViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ForwardPickerUiState())
    val state: StateFlow<ForwardPickerUiState> = _state.asStateFlow()

    private var loaded = false

    fun load(excludeChatUuid: String?) {
        if (loaded) return
        loaded = true
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            chatRepository.loadChats().collect { result ->
                when (result) {
                    is Resource.Success -> _state.value = ForwardPickerUiState(
                        chats = result.data.orEmpty().filter { it.chatUuid != excludeChatUuid },
                        isLoading = false
                    )
                    is Resource.Error -> _state.value = _state.value.copy(isLoading = false)
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    companion object {
        const val ARG_CURRENT_CHAT = "current_chat_uuid"
    }
}
