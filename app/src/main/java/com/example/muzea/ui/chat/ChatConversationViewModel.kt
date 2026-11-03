package com.example.muzea.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.domain.chat.ChatMessageReducer
import com.example.muzea.R
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
/** Файл, выбранный для отправки, и состояние его загрузки. */
data class PendingAttachment(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val type: String,
    val prepared: PreparedAttachment? = null,
    val uploaded: MessageAttachment? = null,
    val error: String? = null
) {
    val isUploaded: Boolean get() = uploaded != null
}

data class ChatConversationUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val historyReached: Boolean = false,
    val error: String? = null,
    /** Квитанции участников: из них считаются галочки и «кто прочитал». */
    val receipts: List<ChatReceipt> = emptyList(),
    /** true после первой успешной загрузки квитанций. */
    val receiptsLoaded: Boolean = false,
    /** Статус своих сообщений: uuid → галочка. */
    val statuses: Map<String, MessageDeliveryStatus> = emptyMap(),
    /** Локальные сообщения, отправка которых не удалась. */
    val failedMessages: Set<String> = emptySet(),
    /** Выбранные, но ещё не отправленные вложения. */
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isUploadingAttachments: Boolean = false
)

@HiltViewModel
class ChatConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    chatUserIdentity: ChatUserIdentity,
    private val mediaFileReader: MediaFileReader,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
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
    @Volatile
    private var pollingPaused = false
    private var lastMarkedReadUuid: String? = null
    private var lastMarkedDeliveredUuid: String? = null

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

    /** @return true, если запрос завершился успешно (нужно для backoff-а). */
    private suspend fun loadOnce(): Boolean {
        var success = true
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
                    refreshReceipts()
                }

                is Resource.Error -> {
                    success = false
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
        return success
    }

    /** Пауза опроса, когда экран не виден: не тратим сеть и батарею. */
    fun pausePolling() {
        pollingPaused = true
    }

    fun resumePolling() {
        pollingPaused = false
        if (pollingJob?.isActive != true) startPolling()
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        val state = _uiState.value
        val attachments = state.pendingAttachments
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (attachments.any { !it.isUploaded }) {
            viewModelScope.launch { _sendError.emit(appContext.getString(R.string.attachment_wait_upload)) }
            return
        }

        val uploaded = attachments.mapNotNull { it.uploaded }
        val type = messageTypeFor(uploaded)
        val optimistic = Message(
            messageUuid = LOCAL_PREFIX + System.currentTimeMillis(),
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = trimmed,
            messageType = type,
            replyToMessageUuid = null,
            isEdited = false,
            isDeleted = false,
            isPinned = false,
            createdAt = null,
            updatedAt = null,
            attachments = uploaded
        )
        mergeMessages(listOf(optimistic))
        clearPendingAttachments()

        viewModelScope.launch {
            messageRepository.sendMessage(
                chatUuid = chatUuid,
                text = trimmed,
                attachments = uploaded.mapNotNull { it.attachmentUuid }
            ).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        applyServerEcho(optimistic.messageUuid, result.data!!)
                    }
                    is Resource.Error -> {
                        markFailed(optimistic.messageUuid)
                        _sendError.emit(result.message ?: "Failed to send message")
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    /** Добавляет выбранные в системном пикере файлы и сразу загружает их. */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAttachments = true)
            try {
                for (uri in uris) {
                    val prepared = try {
                        mediaFileReader.prepare(uri)
                    } catch (e: Exception) {
                        _sendError.emit(e.message ?: appContext.getString(R.string.attachment_cannot_attach))
                        continue
                    }

                    val id = java.util.UUID.randomUUID().toString()
                    val pending = PendingAttachment(
                        id = id,
                        name = prepared.name,
                        size = prepared.size,
                        mimeType = prepared.mimeType,
                        type = prepared.type,
                        prepared = prepared
                    )
                    _uiState.value = _uiState.value.copy(
                        pendingAttachments = _uiState.value.pendingAttachments + pending
                    )
                    uploadPending(id)
                }
            } finally {
                _uiState.value = _uiState.value.copy(isUploadingAttachments = false)
            }
        }
    }

    fun removePendingAttachment(id: String) {
        val pending = _uiState.value.pendingAttachments.firstOrNull { it.id == id } ?: return
        mediaFileReader.cleanup(pending.prepared)
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments.filterNot { it.id == id }
        )
    }

    fun retryPendingAttachment(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAttachments = true)
            try {
                uploadPending(id)
            } finally {
                _uiState.value = _uiState.value.copy(isUploadingAttachments = false)
            }
        }
    }

    private suspend fun uploadPending(id: String) {
        val pending = _uiState.value.pendingAttachments.firstOrNull { it.id == id } ?: return
        val prepared = pending.prepared ?: return
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments.map {
                if (it.id == id) it.copy(error = null) else it
            }
        )

        when (val result = messageRepository.uploadAttachment(chatUuid, prepared)) {
            is Resource.Success -> _uiState.value = _uiState.value.copy(
                pendingAttachments = _uiState.value.pendingAttachments.map {
                    if (it.id == id) it.copy(uploaded = result.data) else it
                }
            )
            is Resource.Error -> _uiState.value = _uiState.value.copy(
                pendingAttachments = _uiState.value.pendingAttachments.map {
                    if (it.id == id) it.copy(error = result.message) else it
                }
            )
            is Resource.Loading -> Unit
        }
    }

    private fun clearPendingAttachments() {
        _uiState.value.pendingAttachments.forEach { mediaFileReader.cleanup(it.prepared) }
        _uiState.value = _uiState.value.copy(pendingAttachments = emptyList())
    }

    private fun messageTypeFor(attachments: List<MessageAttachment>): String {
        if (attachments.isEmpty()) return "TEXT"
        if (attachments.all { it.type == "IMAGE" }) return "IMAGE"
        if (attachments.all { it.type == "AUDIO" }) return "AUDIO"
        if (attachments.size == 1 && attachments.single().type == "VIDEO") return "VIDEO"
        return "FILE"
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
                    is Resource.Error -> {
                        markFailed(optimistic.messageUuid)
                        _sendError.emit(result.message ?: "Failed to send GIF")
                    }
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
        _uiState.value = withStatuses(
            _uiState.value.copy(
                messages = reducer.applyServerEcho(_uiState.value.messages, localUuid, serverMessage),
                failedMessages = _uiState.value.failedMessages - localUuid
            )
        )
        markLatestAsRead()
        markLatestAsDelivered()
    }

    private fun markFailed(localUuid: String) {
        _uiState.value = withStatuses(
            _uiState.value.copy(failedMessages = _uiState.value.failedMessages + localUuid)
        )
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
        _uiState.value = withStatuses(
            _uiState.value.copy(messages = reducer.merge(_uiState.value.messages, incoming))
        )
        markLatestAsRead()
        markLatestAsDelivered()
    }

    /**
     * Подтверждает доставку ленты до последнего полученного сообщения.
     * Watermark на сервере двигается только вперёд, поэтому повторный вызов
     * безопасен.
     */
    private fun markLatestAsDelivered() {
        val latest = _uiState.value.messages.lastOrNull {
            it.messageId != null && !it.messageUuid.startsWith(LOCAL_PREFIX)
        } ?: return
        if (latest.messageUuid == lastMarkedDeliveredUuid) return
        lastMarkedDeliveredUuid = latest.messageUuid
        viewModelScope.launch {
            if (!messageRepository.markMessagesAsDelivered(chatUuid, latest.messageUuid)) {
                lastMarkedDeliveredUuid = null
            }
        }
    }

    /**
     * Подтягивает квитанции участников вместе с очередным опросом сообщений.
     * Ошибку не показываем: галочки просто останутся на прошлом значении.
     */
    private suspend fun refreshReceipts() {
        val receipts = messageRepository.loadChatReceipts(chatUuid) ?: return
        _uiState.value = withStatuses(
            _uiState.value.copy(receipts = receipts, receiptsLoaded = true)
        )
    }

    private fun withStatuses(state: ChatConversationUiState): ChatConversationUiState =
        state.copy(
            statuses = MessageStatusCalculator.statuses(
                messages = state.messages,
                receipts = state.receipts,
                myUserUuid = myUserUuid,
                failedMessages = state.failedMessages,
                receiptsLoaded = state.receiptsLoaded
            )
        )

    /**
     * Загружает только что записанное голосовое/видео-сообщение и отправляет его.
     * Тип VOICE/VIDEO_NOTE передаётся явно — по нему UI рисует волну/кружок.
     */
    fun sendRecordedAttachment(prepared: PreparedAttachment, messageType: String) {
        val localUuid = LOCAL_PREFIX + System.currentTimeMillis()
        val localAttachment = MessageAttachment(
            fileUrl = prepared.file.absolutePath,
            thumbnailUrl = prepared.thumbnail?.absolutePath,
            type = prepared.type,
            width = prepared.width,
            height = prepared.height,
            attachmentUuid = null,
            fileName = prepared.name,
            fileSize = prepared.size,
            mimeType = prepared.mimeType,
            duration = prepared.duration
        )
        val optimistic = Message(
            messageUuid = localUuid,
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = "",
            messageType = messageType,
            replyToMessageUuid = null,
            isEdited = false,
            isDeleted = false,
            isPinned = false,
            createdAt = null,
            updatedAt = null,
            attachments = listOf(localAttachment)
        )
        // Пузырь появляется сразу, ещё до загрузки: на слабой сети иначе
        // пользователь несколько секунд не видит, что запись вообще есть.
        mergeMessages(listOf(optimistic))

        viewModelScope.launch {
            when (val uploaded = messageRepository.uploadAttachment(chatUuid, prepared)) {
                is Resource.Success -> {
                    val attachment = uploaded.data ?: return@launch
                    replaceLocalAttachment(localUuid, attachment)
                    mediaFileReader.cleanup(prepared)

                    messageRepository.sendMessage(
                        chatUuid = chatUuid,
                        text = "",
                        attachments = listOfNotNull(attachment.attachmentUuid),
                        messageType = messageType
                    ).collect { result ->
                        when (result) {
                            is Resource.Success ->
                                applyServerEcho(localUuid, result.data!!)
                            is Resource.Error -> {
                                markFailed(localUuid)
                                _sendError.emit(result.message ?: "Failed to send message")
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
                is Resource.Error -> {
                    // Файл не удаляем: запись остаётся в пузыре и её можно
                    // перезаписать/отправить позже, когда появится сеть.
                    markFailed(localUuid)
                    _sendError.emit(uploaded.message ?: "Upload failed")
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun replaceLocalAttachment(localUuid: String, attachment: MessageAttachment) {
        _uiState.value = withStatuses(
            _uiState.value.copy(
                messages = _uiState.value.messages.map { message ->
                    if (message.messageUuid == localUuid) {
                        message.copy(attachments = listOf(attachment))
                    } else {
                        message
                    }
                }
            )
        )
    }

    /** Скачивает документ во временный файл для открытия системным приложением. */
    suspend fun downloadAttachment(attachment: MessageAttachment): java.io.File? {
        val uuid = attachment.attachmentUuid ?: return null
        return messageRepository.downloadAttachment(uuid, attachment.fileName)
    }

    /** Статус сообщения для адаптера; null — у входящих и пока неизвестных. */
    fun deliveryStatus(messageUuid: String): MessageDeliveryStatus? =
        _uiState.value.statuses[messageUuid]

    /** Кто прочитал/получил сообщение — для шторки статуса. */
    fun participantStatuses(message: Message): List<ParticipantDeliveryStatus> =
        MessageStatusCalculator.participantStatuses(
            message = message,
            receipts = _uiState.value.receipts,
            myUserUuid = myUserUuid
        )

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
                val success = if (pollingPaused) true else loadOnce()
                // На слабой сети не долбим сервер каждые 3 секунды.
                delay(if (success) POLL_INTERVAL_MS else ERROR_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    /** Отправить жалобу на сообщение. Возвращает true, если сервер её принял. */
    suspend fun reportMessage(messageUuid: String, reason: String): Boolean =
        messageRepository.reportMessage(messageUuid, reason)

    /** Заблокировать автора сообщения. */
    suspend fun blockUser(userUuid: String): Boolean =
        messageRepository.blockUser(userUuid)

    companion object {
        const val ARG_CHAT_UUID = "chat_uuid"
        private const val LOCAL_PREFIX = "local-"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val ERROR_POLL_INTERVAL_MS = 15_000L
        private const val PAGE_SIZE = 50
    }
}