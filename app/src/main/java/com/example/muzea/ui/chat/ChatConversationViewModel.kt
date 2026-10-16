package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.domain.chat.ChatMessageReducer
import com.example.muzea.core.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/** Единое состояние экрана переписки. */
data class ChatConversationUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val historyReached: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    chatUserIdentity: ChatUserIdentity
) : ViewModel() {

    private val chatUuid: String = savedStateHandle.get<String>(ARG_CHAT_UUID).orEmpty()
    val myUserUuid: String? = chatUserIdentity.userUuid

    // Стартуем с кэша: при повторном входе переписка видна сразу, а refresh()
    // и опрос догружают свежие сообщения фоном. Кэш хранит сообщения в порядке
    // сервера (сначала новые), поэтому сортируем их до показа — иначе при
    // открытии чата порядок был бы перевёрнут до первого сетевого обновления.
    private val reducer = ChatMessageReducer()

    private val _uiState = MutableStateFlow(
        ChatConversationUiState(messages = reducer.seed(messageRepository.cachedMessages(chatUuid)))
    )
    val uiState: StateFlow<ChatConversationUiState> = _uiState.asStateFlow()

    private val _sendError = MutableSharedFlow<String>()
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    private var pollingJob: Job? = null
    private var lastMarkedReadUuid: String? = null

    init {
        // Опрос сам выполняет первый запрос, поэтому отдельный refresh() в init
        // был бы вторым обращением к серверу подряд при каждом входе в чат.
        startPolling()
    }

    /** Разовая загрузка вне цикла опроса: pull-to-refresh и ручное обновление. */
    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    /**
     * Догрузка истории вверх: подтягивает сообщения старше самого раннего в
     * списке. Пока идёт запрос или история исчерпана, повторные вызовы
     * игнорируются. Локальные (ещё не отправленные) сообщения пропускаем —
     * у них нет серверного uuid.
     */
    fun loadOlder() {
        val state = _uiState.value
        if (state.isLoadingMore || state.historyReached) return

        val oldest = state.messages.firstOrNull {
            it.messageUuid.isNotBlank() && !it.messageUuid.startsWith(LOCAL_PREFIX)
        } ?: return

        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            messageRepository.loadMessagesBefore(chatUuid, oldest.messageUuid, PAGE_SIZE)
                .collect { result ->
                    when (result) {
                        is Resource.Loading -> Unit
                        is Resource.Success -> {
                            val older = result.data.orEmpty()
                            mergeMessages(older)
                            _uiState.value = _uiState.value.copy(
                                isLoadingMore = false,
                                // Неполная страница означает, что история кончилась.
                                historyReached = older.size < PAGE_SIZE
                            )
                        }
                        is Resource.Error ->
                            _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    }
                }
        }
    }

    private suspend fun loadOnce() {
        messageRepository.loadMessages(chatUuid).collect { result ->
            when (result) {
                // Спиннер показываем только когда показать нечего: переписка из
                // кэша уже на экране, и мигать индикатором при входе незачем.
                is Resource.Loading -> _uiState.value =
                    _uiState.value.copy(isLoading = _uiState.value.messages.isEmpty())

                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    val incoming = result.data ?: emptyList()
                    cacheNewGifs(incoming)
                    mergeMessages(incoming)
                }

                is Resource.Error -> _uiState.value =
                    _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val optimistic = Message(
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
            messageRepository.sendMessage(chatUuid, trimmed).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        applyServerEcho(optimistic.messageUuid, result.data!!)
                    }
                    is Resource.Error -> {
                        _sendError.emit(result.message ?: "Failed to send message")
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun sendGif(gif: Gif) {
        val optimistic = Message(
            messageUuid = LOCAL_PREFIX + System.currentTimeMillis(),
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = "",
            messageType = "GIF",
            replyToMessageUuid = null,
            isEdited = false,
            isDeleted = false,
            isPinned = false,
            createdAt = null,
            updatedAt = null,
            attachments = listOf(
                MessageAttachment(
                    fileUrl = gif.url,
                    thumbnailUrl = gif.previewUrl,
                    type = "GIF",
                    width = gif.width,
                    height = gif.height,
                    mp4Url = gif.mp4Url
                )
            )
        )
        mergeMessages(listOf(optimistic))

        // Сразу добавляем в кэш «Мои»/«Этот чат» (текущий пользователь — автор).
        messageRepository.cacheSentGif(chatUuid, gif, mine = true)

        viewModelScope.launch {
            messageRepository.sendGif(chatUuid, gif).collect { result ->
                when (result) {
                    is Resource.Success -> applyServerEcho(optimistic.messageUuid, result.data!!)
                    is Resource.Error ->
                        _sendError.emit(result.message ?: "Failed to send GIF")
                    is Resource.Loading -> {}
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
    private fun applyServerEcho(localUuid: String, serverMessage: Message) {
        _uiState.value = _uiState.value.copy(
            messages = reducer.applyServerEcho(_uiState.value.messages, localUuid, serverMessage)
        )
        markLatestAsRead()
    }

    /**
     * Новая гифка в переписке (в т.ч. от другого участника) — добавляем её в
     * кэш «Этот чат»/«Мои» из уже полученного сообщения, без обращения к сети.
     * Никакого сброса кэша: список просто пополняется сверху.
     */
    private fun cacheNewGifs(incoming: List<Message>) {
        if (incoming.isEmpty()) return
        val known = _uiState.value.messages.mapTo(HashSet()) { it.messageUuid }
        incoming.forEach { message ->
            if (message.messageUuid in known || !message.isGifMessage()) return@forEach
            val attachment = message.attachments.firstOrNull {
                it.type?.equals("GIF", ignoreCase = true) == true
            } ?: return@forEach
            val gif = Gif(
                provider = "",
                providerGifId = "",
                title = null,
                url = attachment.fileUrl,
                previewUrl = attachment.thumbnailUrl,
                width = attachment.width,
                height = attachment.height,
                mp4Url = attachment.mp4Url,
                gifKey = attachment.gifKey
            )
            messageRepository.cacheSentGif(chatUuid, gif, mine = message.senderUuid == myUserUuid)
        }
    }

    private fun Message.isGifMessage(): Boolean =
        messageType?.equals("GIF", ignoreCase = true) == true ||
            attachments.any { it.type?.equals("GIF", ignoreCase = true) == true }

    private fun mergeMessages(incoming: List<Message>) {
        _uiState.value = _uiState.value.copy(
            messages = reducer.merge(_uiState.value.messages, incoming)
        )
        markLatestAsRead()
    }

    private fun markLatestAsRead() {
        val latest = _uiState.value.messages.lastOrNull {
            it.messageUuid.isNotBlank() && !it.messageUuid.startsWith(LOCAL_PREFIX)
        } ?: return
        if (latest.messageUuid == lastMarkedReadUuid) return
        lastMarkedReadUuid = latest.messageUuid
        viewModelScope.launch {
            if (!messageRepository.markMessagesAsRead(chatUuid, latest.messageUuid)) {
                lastMarkedReadUuid = null
            }
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
        const val ARG_CHAT_UUID = "chat_uuid"
        private const val LOCAL_PREFIX = "local-"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val PAGE_SIZE = 50
    }
}