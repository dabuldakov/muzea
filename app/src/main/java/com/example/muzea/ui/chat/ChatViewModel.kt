package com.example.muzea.ui.chat

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.ContactRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    /**
     * Список чатов — состояние экрана, поэтому StateFlow, а не SharedFlow:
     * StateFlow хранит последнее значение и отдаёт его каждому новому
     * подписчику. Возвращаясь из чата, экран получает список мгновенно, без
     * ожидания ответа сервера и без индикатора загрузки.
     *
     * Стартуем с кэша, чтобы список был виден до первой сетевой загрузки.
     */
    private val cachedChats = chatRepository.cachedChats()
    private val _chats = MutableStateFlow(cachedChats)
    val chats: StateFlow<List<ChatResponse>> = _chats.asStateFlow()

    /**
     * Спиннер нужен только когда показать нечего: при непустом кэше или уже
     * отрисованном списке фоновое обновление не должно его показывать.
     */
    private val _isLoadingChats = MutableStateFlow(cachedChats.isEmpty())
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    /**
     * Ошибка загрузки списка.
     *
     * Именно StateFlow, а не одноразовый SharedFlow: ошибка приходит из
     * фоновой загрузки, которую запускает экран, и подписчик может
     * появиться позже. Одноразовый поток молча потерял бы такое событие, и
     * пользователь не увидел бы, почему список не обновился.
     */
private val _chatsError = MutableStateFlow<String?>(null)
    val chatsError: StateFlow<String?> = _chatsError.asStateFlow()

    private var chatsJob: Job? = null

    /**
     * Контакты тоже StateFlow: диалог участников группы переоткрывается и
     * должен сразу показать прошлый список, а не пустоту.
     */
    private val _contactsResult =
        MutableStateFlow<NetworkResult<List<ContactResponse>>>(NetworkResult.Loading())
    val contactsResult: StateFlow<NetworkResult<List<ContactResponse>>> =
        _contactsResult.asStateFlow()

    private val _createGroupChatResult = MutableSharedFlow<NetworkResult<ChatResponse>>()
    val createGroupChatResult: SharedFlow<NetworkResult<ChatResponse>> =
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
                    is NetworkResult.Loading -> _isLoadingChats.value = _chats.value.isEmpty()
                    is NetworkResult.Success -> {
                        _chats.value = result.data ?: emptyList()
                        _isLoadingChats.value = false
                        _chatsError.value = null
                    }
                    is NetworkResult.Error -> {
                        _isLoadingChats.value = false
                        // Список из кэша ценнее сообщения об ошибке: не даём
                        // ошибке занять место данных, но и молча не проглатываем.
                        _chatsError.value =
                            if (_chats.value.isEmpty()) {
                                result.message ?: "Unknown error"
                            } else {
                                "Error: ${result.message}"
                            }
                    }
                }
            }
        }
    }

    /** Экран показал ошибку: сбрасываем, чтобы тост не повторялся при переподписке. */
    fun chatsErrorShown() {
        _chatsError.value = null
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