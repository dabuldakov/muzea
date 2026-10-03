package com.example.muzea.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Единое состояние списка чатов. */
data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    // Стартуем с кэша, чтобы список был виден до первой сетевой загрузки.
    private val cachedChats = chatRepository.cachedChats()
    private val _uiState = MutableStateFlow(
        ChatListUiState(chats = cachedChats, isLoading = cachedChats.isEmpty())
    )
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var chatsJob: Job? = null

    /**
     * Контакты тоже StateFlow: диалог участников группы переоткрывается и
     * должен сразу показать прошлый список, а не пустоту.
     */
    private val _contactsResult =
        MutableStateFlow<NetworkResult<List<Contact>>>(NetworkResult.Loading())
    val contactsResult: StateFlow<NetworkResult<List<Contact>>> =
        _contactsResult.asStateFlow()

    private val _createGroupChatResult = MutableSharedFlow<NetworkResult<Chat>>()
    val createGroupChatResult: SharedFlow<NetworkResult<Chat>> =
        _createGroupChatResult.asSharedFlow()

    private val _addParticipantsResult = MutableSharedFlow<NetworkResult<Unit>>()
    val addParticipantsResult: SharedFlow<NetworkResult<Unit>> =
        _addParticipantsResult.asSharedFlow()

    fun loadChats() {
        // Экран дёргает загрузку из onResume и из автообновления раз в 8 секунд.
        // Без этой защиты запросы накладывались бы друг на друга.
        if (chatsJob?.isActive == true) return
        chatsJob = viewModelScope.launch {
            chatRepository.loadChats().collect { result ->
                when (result) {
                    // Спиннер только когда показать нечего.
                    is NetworkResult.Loading -> _uiState.value =
                        _uiState.value.copy(isLoading = _uiState.value.chats.isEmpty())

                    is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                        chats = result.data ?: emptyList(),
                        isLoading = false,
                        error = null
                    )

                    is NetworkResult.Error -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        // Список из кэша ценнее сообщения об ошибке: не даём
                        // ошибке занять место данных, но и молча не проглатываем.
                        error = if (_uiState.value.chats.isEmpty()) {
                            result.message ?: "Unknown error"
                        } else {
                            "Error: ${result.message}"
                        }
                    )
                }
            }
        }
    }

    /** Экран показал ошибку: сбрасываем, чтобы тост не повторялся при переподписке. */
    fun chatsErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadContacts() {
        viewModelScope.launch {
            contactRepository.loadContacts().collect { result ->
                _contactsResult.value = result
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
