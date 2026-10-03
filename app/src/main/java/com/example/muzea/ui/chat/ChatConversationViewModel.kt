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

    // Стартуем с кэша: при повторном входе переписка видна сразу, а refresh()
    // и опрос догружают свежие сообщения фоном. Кэш хранит сообщения в порядке
    // сервера (сначала новые), поэтому сортируем их до показа — иначе при
    // открытии чата порядок был бы перевёрнут до первого сетевого обновления.
    private val _messages = MutableStateFlow(
        chatRepository.cachedMessages(chatUuid).sortedWith(MessageComparator())
    )
    val messages: StateFlow<List<MessageResponse>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sendError = MutableSharedFlow<String>()
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    private var pollingJob: Job? = null
    private var lastMarkedReadUuid: String? = null

    // serverUuid -> localUuid для наших отправленных сообщений: серверное эхо
    // показываем не отдельным элементом, а обновляем уже существующий пузырь,
    // чтобы DiffUtil не удалял и не вставлял строку (иначе список мигает).
    private val serverToLocal = HashMap<String, String>()

    init {
        // Опрос сам выполняет первый запрос, поэтому отдельный refresh() в init
        // был бы вторым обращением к серверу подряд при каждом входе в чат.
        startPolling()
    }

    /** Разовая загрузка вне цикла опроса: pull-to-refresh и ручное обновление. */
    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    private suspend fun loadOnce() {
        chatRepository.loadMessages(chatUuid).collect { result ->
            when (result) {
                // Спиннер показываем только когда показать нечего: переписка из
                // кэша уже на экране, и мигать индикатором при входе незачем.
                is NetworkResult.Loading -> _isLoading.value = _messages.value.isEmpty()
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

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val optimistic = MessageResponse(
            messageUuid = LOCAL_PREFIX + System.currentTimeMillis(),
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
                        applyServerEcho(optimistic.messageUuid, result.data!!)
                    }
                    is NetworkResult.Error -> {
                        _sendError.emit(result.message ?: "Failed to send message")
                    }
                    is NetworkResult.Loading -> {}
                }
            }
        }
    }

    /**
     * Обновляет локальный пузырь данными серверного эха, не меняя его
     * идентификатор в списке. Раньше локальное сообщение удалялось, а серверное
     * вставлялось заново: DiffUtil считал строку другой и перерисовывал её —
     * при отправке список мигал. Теперь это обычное изменение содержимого.
     */
    private fun applyServerEcho(localUuid: String, serverMessage: MessageResponse) {
        serverToLocal[serverMessage.messageUuid] = localUuid
        _messages.value = _messages.value
            .map { existing ->
                if (existing.messageUuid == localUuid) {
                    serverMessage.copy(messageUuid = localUuid)
                } else {
                    existing
                }
            }
            .sortedWith(MessageComparator())
        markLatestAsRead()
    }

    private fun mergeMessages(incoming: List<MessageResponse>) {
        val merged = LinkedHashMap<String, MessageResponse>()
        for (m in incoming) {
            // Эхо своих сообщений уже показано локальным пузырём — не дублируем.
            if (serverToLocal.containsKey(m.messageUuid)) continue
            merged[m.messageUuid] = m
        }
        for (m in _messages.value) merged[m.messageUuid] = m
        _messages.value = merged.values.toList().sortedWith(MessageComparator())
        markLatestAsRead()
    }

    private fun markLatestAsRead() {
        val latest = _messages.value.lastOrNull {
            it.messageUuid.isNotBlank() && !it.messageUuid.startsWith(LOCAL_PREFIX)
        } ?: return
        if (latest.messageUuid == lastMarkedReadUuid) return
        lastMarkedReadUuid = latest.messageUuid
        viewModelScope.launch {
            if (!chatRepository.markMessagesAsRead(chatUuid, latest.messageUuid)) {
                lastMarkedReadUuid = null
            }
        }
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
                loadOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val LOCAL_PREFIX = "local-"
        private const val POLL_INTERVAL_MS = 3_000L
    }
}