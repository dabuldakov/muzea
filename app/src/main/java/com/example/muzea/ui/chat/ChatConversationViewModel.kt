package com.example.muzea.ui.chat

import android.net.Uri
import java.io.File
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.domain.model.MessagePreview
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.data.repository.OutboxEvent
import com.example.muzea.data.repository.PendingAttachmentStore
import com.example.muzea.data.repository.StoredPendingAttachment
import com.example.muzea.data.repository.OutboxItem
import com.example.muzea.data.repository.OutboxSender
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
    val error: String? = null,
    val progress: Int = 0
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
    val isUploadingAttachments: Boolean = false,
    /** Сообщение, на которое сейчас отвечаем (null — обычная отправка). */
    val replyTo: Message? = null,
    /** Сообщение, которое сейчас редактируем (null — обычная отправка). */
    val editing: Message? = null,
    /** Закреплённые сообщения чата (для панели сверху). */
    val pinnedMessages: List<Message> = emptyList()
)

@HiltViewModel
class ChatConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val outboxSender: OutboxSender,
    private val networkMonitor: NetworkMonitor,
    chatUserIdentity: ChatUserIdentity,
    private val mediaFileReader: MediaFileReader,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val pendingStore: PendingAttachmentStore = PendingAttachmentStore(appContext)
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

    /** Нейтральные уведомления (без префикса ошибки): например, «переслано». */
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var pollingJob: Job? = null
    @Volatile
    private var pollingPaused = false
    private var lastMarkedReadUuid: String? = null
    /** Первая загрузка экрана уже завершилась: больше не мигаем спиннером. */
    private var initialLoadDone = false
    private var lastMarkedDeliveredUuid: String? = null
    /** Ошибку по сообщению показываем один раз, чтобы не спамить тостами. */
    private val notifiedFailures = mutableSetOf<String>()

    init {
        restorePendingMessages()
        restorePendingAttachments()
        observeOutbox()
        observeNetworkWakeup()
        // Опрос сам выполняет первый запрос, поэтому отдельный refresh() в init
        // был бы вторым обращением к серверу подряд при каждом входе в чат.
        startPolling()
        viewModelScope.launch { refreshPinned() }
    }

    /**
     * После блокировки/авиарежима сеть возвращается уже после onStart.
     * Перезапускаем polling, чтобы не ждать 60-секундный offline-delay.
     */
    private fun observeNetworkWakeup() {
        viewModelScope.launch {
            var wasOnline = networkMonitor.status.value.online
            networkMonitor.status.collect { status ->
                if (status.online && !wasOnline && !pollingPaused) {
                    pollingJob?.cancel()
                    pendingFullSync = true
                    startPolling()
                    retryQueuedUploads()
                }
                wasOnline = status.online
            }
        }
    }

    /** Поднимаем из очереди сообщения, не отправленные в прошлой сессии. */
    private fun restorePendingMessages() {
        val pending = outboxSender.pendingForChat(chatUuid).map { item ->
            Message(
                messageUuid = item.localMessageUuid,
                chatUuid = item.chatUuid,
                senderId = null,
                senderUuid = myUserUuid,
                senderName = null,
                senderAvatar = null,
                text = item.text,
                messageType = item.messageType,
                replyToMessageUuid = item.replyToMessageUuid,
                isEdited = false,
                isDeleted = false,
                isPinned = false,
                createdAt = null,
                updatedAt = null,
                attachments = item.attachments,
                power = item.power
            )
        }
        if (pending.isNotEmpty()) mergeMessages(pending)
    }

    private fun observeOutbox() {
        viewModelScope.launch {
            outboxSender.events.collect { event ->
                when (event) {
                    is OutboxEvent.Sent -> if (event.item.chatUuid == chatUuid) {
                        notifiedFailures.remove(event.item.localMessageUuid)
                        applyServerEcho(event.item.localMessageUuid, event.message)
                    }
                    is OutboxEvent.Failed -> if (event.item.chatUuid == chatUuid) {
                        markFailed(event.item.localMessageUuid)
                        if (notifiedFailures.add(event.item.localMessageUuid)) {
                            _sendError.emit(event.reason)
                        }
                    }
                }
            }
        }
    }

    /** Разовая загрузка вне цикла опроса: pull-to-refresh и ручное обновление. */
    fun refresh() {
        viewModelScope.launch {
            loadOnce()
            refreshPinned()
        }
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
    private suspend fun loadOnce(incremental: Boolean = false): Boolean {
        var success = true
        val since = lastServerCreatedAt
        val source = if (incremental && since != null) {
            messageRepository.loadMessagesAfter(chatUuid, since)
        } else {
            messageRepository.loadMessages(chatUuid)
        }
        source.collect { result ->
            when (result) {
                // Спиннер показываем только когда показать нечего: переписка из
                // кэша уже на экране, и мигать индикатором при входе незачем.
                is Resource.Loading -> _uiState.value = _uiState.value.copy(
                    isLoading = _uiState.value.messages.isEmpty() && !initialLoadDone
                )

                is Resource.Success -> {
                    initialLoadDone = true
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    val incoming = result.data ?: emptyList()
                    rememberServerTime(incoming)
                    cacheNewGifs(incoming)
                    mergeMessages(incoming)
                    refreshReceipts(force = incoming.isNotEmpty())
                }

                is Resource.Error -> {
                    success = false
                    initialLoadDone = true
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
        return success
    }

    /**
     * Сверка правок без полной страницы: запрашиваем только сообщения,
     * изменённые с прошлой проверки. Полная страница на 50 сообщений весит
     * ~67 КБ, а изменений обычно нет вовсе — ответ пустой, ~2 байта.
     *
     * @return true, если запрос завершился успешно.
     */
    private suspend fun loadUpdatedOnce(): Boolean {
        // Сдвиг в прошлое страхует от разницы часов и от строк, зафиксированных
        // уже после запроса: повторная обработка тех же сообщений безвредна.
        val since = lastUpdatedCheckAt
            ?: LocalTimeFormatter.serverTimestamp(UPDATED_OVERLAP_SECONDS)
        val updated = messageRepository.loadUpdatedMessages(chatUuid, since) ?: return false
        lastUpdatedCheckAt = LocalTimeFormatter.serverTimestamp(UPDATED_OVERLAP_SECONDS)
        if (updated.isEmpty()) return true
        cacheNewGifs(updated)
        mergeMessages(updated)
        refreshReceipts(force = true)
        return true
    }

    /** Пауза опроса, когда экран не виден: не тратим сеть и батарею. */
    fun pausePolling() {
        pollingPaused = true
        // Не оставляем job спать в delay(60s) за спиной: при возврате
        // на экран запускаем его заново с немедленным запросом.
        pollingJob?.cancel()
        pollingJob = null
    }

    fun resumePolling() {
        pollingPaused = false
        pollingJob?.cancel()
        // Вернулись на экран — один раз сверяемся полной страницей: дельта
        // и правки за это время могли выпасть из окна сверки.
        pendingFullSync = true
        startPolling()
    }

    fun sendText(text: String, power: Int = 0) {
        val editing = _uiState.value.editing
        if (editing != null) {
            submitEdit(editing, text)
            return
        }

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
        val replyToUuid = state.replyTo?.messageUuid?.let { reducer.serverUuid(it) }
        val optimistic = Message(
            messageUuid = LOCAL_PREFIX + System.currentTimeMillis(),
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = trimmed,
            messageType = type,
            replyToMessageUuid = replyToUuid,
            isEdited = false,
            isDeleted = false,
            isPinned = false,
            createdAt = null,
            updatedAt = null,
            attachments = uploaded,
            power = power.coerceIn(0, 100)
        )
        mergeMessages(listOf(optimistic))
        clearPendingAttachments()
        clearReply()

        // Отправка идёт через app-scope очередь: уход из чата или смерть
        // процесса не теряют текст, повтор идемпотентен по clientMessageId.
        outboxSender.enqueue(
            OutboxItem(
                clientMessageId = java.util.UUID.randomUUID().toString(),
                localMessageUuid = optimistic.messageUuid,
                chatUuid = chatUuid,
                text = trimmed,
                messageType = type,
                replyToMessageUuid = replyToUuid,
                attachments = uploaded,
                createdAt = System.currentTimeMillis(),
                power = optimistic.power
            )
        )
    }

    private fun restorePendingAttachments() {
        val stored = pendingStore.load(chatUuid)
        if (stored.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            pendingAttachments = stored.map { toPending(it) }
        )
        if (networkMonitor.status.value.online) retryQueuedUploads()
    }

    private fun retryQueuedUploads() {
        val ids = _uiState.value.pendingAttachments
            .filter { !it.isUploaded && it.error == null }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) uploadPending(id)
        }
    }

    private fun toPending(stored: StoredPendingAttachment): PendingAttachment {
        val file = File(stored.filePath)
        val prepared = PreparedAttachment(
            file = file,
            thumbnail = stored.thumbnailPath?.let(::File)?.takeIf { it.exists() },
            name = stored.name,
            size = stored.size,
            mimeType = stored.mimeType,
            type = stored.type,
            width = stored.width,
            height = stored.height,
            duration = stored.duration
        )
        val uploaded = stored.uploadedUuid?.let { uuid ->
            MessageAttachment(
                fileUrl = "",
                thumbnailUrl = null,
                type = stored.type,
                width = stored.width,
                height = stored.height,
                attachmentUuid = uuid,
                fileName = stored.name,
                fileSize = stored.size,
                mimeType = stored.mimeType,
                duration = stored.duration
            )
        }
        return PendingAttachment(
            id = stored.id,
            name = stored.name,
            size = stored.size,
            mimeType = stored.mimeType,
            type = stored.type,
            prepared = prepared,
            uploaded = uploaded
        )
    }

    private fun persistPending() {
        val stored = _uiState.value.pendingAttachments.mapNotNull { pending ->
            val prepared = pending.prepared ?: return@mapNotNull null
            StoredPendingAttachment(
                id = pending.id,
                name = pending.name,
                size = pending.size,
                mimeType = pending.mimeType,
                type = pending.type,
                filePath = prepared.file.absolutePath,
                thumbnailPath = prepared.thumbnail?.absolutePath,
                width = prepared.width,
                height = prepared.height,
                duration = prepared.duration,
                uploadedUuid = pending.uploaded?.attachmentUuid
            )
        }
        pendingStore.save(chatUuid, stored)
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
                    persistPending()
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
        persistPending()
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

        when (val result = messageRepository.uploadAttachment(chatUuid, prepared) { percent ->
            _uiState.value = _uiState.value.copy(
                pendingAttachments = _uiState.value.pendingAttachments.map {
                    if (it.id == id) it.copy(progress = percent) else it
                }
            )
        }) {
            is Resource.Success -> {
                _uiState.value = _uiState.value.copy(
                    pendingAttachments = _uiState.value.pendingAttachments.map {
                        if (it.id == id) it.copy(uploaded = result.data) else it
                    }
                )
                persistPending()
            }
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
        pendingStore.clear(chatUuid)
        _uiState.value = _uiState.value.copy(pendingAttachments = emptyList())
    }

    /** Ставит или снимает режим ответа на сообщение. */
    fun setReplyTo(message: Message?) {
        _uiState.value = _uiState.value.copy(replyTo = message)
    }

    private fun clearReply() {
        if (_uiState.value.replyTo != null) {
            _uiState.value = _uiState.value.copy(replyTo = null)
        }
    }

    /** Включает режим редактирования; режим ответа при этом сбрасывается. */
    fun startEditing(message: Message) {
        _uiState.value = _uiState.value.copy(editing = message, replyTo = null)
    }

    fun cancelEditing() {
        clearEditing()
    }

    private fun submitEdit(target: Message, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch {
                _sendError.emit(appContext.getString(R.string.error_message_required))
            }
            return
        }
        clearEditing()
        if (trimmed == target.text) return

        viewModelScope.launch {
            messageRepository.editMessage(reducer.serverUuid(target.messageUuid), trimmed).collect { result ->
                when (result) {
                    is Resource.Success -> updateMessage(result.data!!)
                    is Resource.Error ->
                        _sendError.emit(result.message ?: "Failed to edit message")
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun clearEditing() {
        if (_uiState.value.editing != null) {
            _uiState.value = _uiState.value.copy(editing = null)
        }
    }

    private fun updateMessage(updated: Message) {
        // Сервер возвращает сообщение с реальным uuid; в списке наше сообщение
        // может храниться под локальным uuid — приводим к нему, иначе правка
        // не найдёт нужный пузырь.
        val displayUuid = reducer.localUuidFor(updated.messageUuid) ?: updated.messageUuid
        val reconciled = updated.copy(messageUuid = displayUuid)
        // Сервер может вернуть сообщение без вложений (например, при правке
        // подписи к фото). Не теряем уже показанную картинку.
        val existing = _uiState.value.messages.firstOrNull {
            it.messageUuid == displayUuid
        }
        val merged = if (reconciled.attachments.isEmpty() &&
            existing != null && existing.attachments.isNotEmpty()
        ) {
            reconciled.copy(
                attachments = existing.attachments,
                power = if (reconciled.power > 0) reconciled.power else existing.power,
                reactions = reconciled.reactions
            )
        } else {
            reconciled.copy(
                power = if (reconciled.power > 0) reconciled.power else existing?.power ?: 0,
                reactions = reconciled.reactions
            )
        }
        val messages = reducer.updateMessage(_uiState.value.messages, merged)
        _uiState.value = withStatuses(_uiState.value.copy(messages = messages))
        ChatMessagesCache.put(chatUuid, reducer.toServerIdentities(messages))
        // Если правили последнее сообщение — сразу обновляем превью в списке чатов.
        if (messages.lastOrNull()?.messageUuid == merged.messageUuid) {
            ChatListCache.updateLastMessage(chatUuid, previewOf(merged))
        }
    }

    /** Мягко удаляет своё сообщение «для всех». */
    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            val ok = messageRepository.deleteMessage(reducer.serverUuid(message.messageUuid))
            if (ok) {
                removeMessageLocally(message.messageUuid)
                clearComposerFor(message.messageUuid)
            } else {
                _sendError.emit(appContext.getString(R.string.delete_message_failed))
            }
        }
    }

    /**
     * Удалённое сообщение не показываем пузырём-заглушкой: убираем его из
     * списка, а RecyclerView анимирует исчезновение (scale-to-point). Превью
     * чата откатывается на предыдущее сообщение.
     */
    private fun removeMessageLocally(messageUuid: String) {
        val remaining = _uiState.value.messages.filterNot { it.messageUuid == messageUuid }
        _uiState.value = withStatuses(_uiState.value.copy(messages = remaining))
        ChatMessagesCache.put(chatUuid, reducer.toServerIdentities(remaining))
        val last = remaining.lastOrNull()
        if (last == null) {
            ChatListCache.clearLastMessage(chatUuid)
        } else {
            ChatListCache.updateLastMessage(chatUuid, previewOf(last))
        }
    }

    private fun previewOf(message: Message) = MessagePreview(
        messageUuid = reducer.serverUuid(message.messageUuid),
        text = replyPreviewText(appContext, message),
        senderId = message.senderId,
        senderName = message.senderName,
        createdAt = message.createdAt
    )

    /** Сбрасывает «ответ»/«редактирование», если они ссылаются на это сообщение. */
    private fun clearComposerFor(messageUuid: String) {
        val state = _uiState.value
        val editingThis = state.editing?.messageUuid == messageUuid
        val replyThis = state.replyTo?.messageUuid == messageUuid
        if (editingThis || replyThis) {
            _uiState.value = state.copy(
                editing = if (editingThis) null else state.editing,
                replyTo = if (replyThis) null else state.replyTo
            )
        }
    }

    /**
     * Пересылает сообщение в выбранный чат. Если пользователь добавил
     * комментарий, он отправляется в тот же чат отдельным сообщением.
     */
    fun forwardMessage(message: Message, targetChatUuid: String, comment: String? = null) {
        forwardMessages(listOf(message), targetChatUuid, comment)
    }

    /**
     * Пересылает пачку сообщений. Комментарий пользователя отправляется один
     * раз, после всех пересланных сообщений.
     */
    fun forwardMessages(messages: List<Message>, targetChatUuid: String, comment: String? = null) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            var forwarded = false
            for (message in messages) {
                messageRepository.forwardMessage(reducer.serverUuid(message.messageUuid), targetChatUuid)
                    .collect { result ->
                        when (result) {
                            is Resource.Success -> forwarded = true
                            is Resource.Error ->
                                _sendError.emit(appContext.getString(R.string.forward_message_failed))
                            is Resource.Loading -> Unit
                        }
                    }
            }
            if (!forwarded) return@launch
            if (!comment.isNullOrBlank()) {
                messageRepository.sendMessage(
                    targetChatUuid, comment, emptyList(), "TEXT", null
                ).collect { result ->
                    if (result is Resource.Error) {
                        _sendError.emit(
                            result.message ?: appContext.getString(R.string.forward_message_failed)
                        )
                    }
                }
            }
            _events.emit(appContext.getString(R.string.message_forwarded))
        }
    }

    /** Закрепляет сообщение в текущем чате. */
    fun pinMessage(message: Message) {
        viewModelScope.launch {
            val ok = messageRepository.pinMessage(chatUuid, reducer.serverUuid(message.messageUuid))
            if (ok) {
                updateMessage(message.copy(isPinned = true))
                refreshPinned()
            } else {
                _sendError.emit(appContext.getString(R.string.pin_message_failed))
            }
        }
    }

    /** Открепляет сообщение. */
    fun unpinMessage(message: Message) {
        viewModelScope.launch {
            val ok = messageRepository.unpinMessage(chatUuid, reducer.serverUuid(message.messageUuid))
            if (ok) {
                updateMessage(message.copy(isPinned = false))
                refreshPinned()
            } else {
                _sendError.emit(appContext.getString(R.string.unpin_message_failed))
            }
        }
    }

    /** Обновляет список закреплённых для панели сверху. */
    private suspend fun refreshPinned() {
        val pinned = messageRepository.loadPinned(chatUuid)
        _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
    }

    private fun messageTypeFor(attachments: List<MessageAttachment>): String {
        if (attachments.isEmpty()) return "TEXT"
        if (attachments.all { it.type == "IMAGE" }) return "IMAGE"
        if (attachments.all { it.type == "AUDIO" }) return "AUDIO"
        if (attachments.size == 1 && attachments.single().type == "VIDEO") return "VIDEO"
        return "FILE"
    }

    fun sendGif(gif: Gif) {
        val replyToUuid = _uiState.value.replyTo?.messageUuid?.let { reducer.serverUuid(it) }
        val optimistic = Message(
            messageUuid = LOCAL_PREFIX + System.currentTimeMillis(),
            chatUuid = chatUuid,
            senderId = null,
            senderUuid = myUserUuid,
            senderName = null,
            senderAvatar = null,
            text = "",
            messageType = "GIF",
            replyToMessageUuid = replyToUuid,
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
        clearReply()

        // Сразу добавляем в кэш «Мои»/«Этот чат» (текущий пользователь — автор).
        messageRepository.cacheSentGif(chatUuid, gif, mine = true)

        viewModelScope.launch {
            messageRepository.sendGif(chatUuid, gif, replyToMessageUuid = replyToUuid).collect { result ->
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
            if (!messageRepository.markMessagesAsDelivered(chatUuid, reducer.serverUuid(latest.messageUuid))) {
                lastMarkedDeliveredUuid = null
            }
        }
    }

    /**
     * Подтягивает квитанции участников, но не чаще [RECEIPTS_REFRESH_INTERVAL_MS]:
     * галочки не обязаны обновляться каждые 3 секунды, а каждый такой запрос —
     * это ~1,25 КБ (в большой группе — десятки килобайт).
     * Ошибку не показываем: галочки просто останутся на прошлом значении.
     */
    private suspend fun refreshReceipts(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastReceiptsRefreshAt < RECEIPTS_REFRESH_INTERVAL_MS) return
        val receipts = messageRepository.loadChatReceipts(chatUuid) ?: return
        lastReceiptsRefreshAt = now
        _uiState.value = withStatuses(
            _uiState.value.copy(receipts = receipts, receiptsLoaded = true)
        )
    }

    /** Свежие квитанции вне общего ритма — например, при открытии «Деталей». */
    suspend fun refreshReceiptsNow() {
        refreshReceipts(force = true)
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
        val replyToUuid = _uiState.value.replyTo?.messageUuid?.let { reducer.serverUuid(it) }
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
            replyToMessageUuid = replyToUuid,
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
        clearReply()

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
                        messageType = messageType,
                        replyToMessageUuid = replyToUuid
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
        replaceMessageAttachments(localUuid, listOf(attachment))
    }

    private fun replaceMessageAttachments(localUuid: String, attachments: List<MessageAttachment>) {
        _uiState.value = withStatuses(
            _uiState.value.copy(
                messages = _uiState.value.messages.map { message ->
                    if (message.messageUuid == localUuid) {
                        message.copy(attachments = attachments)
                    } else {
                        message
                    }
                }
            )
        )
    }

    /**
     * Повторная отправка неудавшегося сообщения. Для локальных голосовых и
     * видео сначала повторно загружает файл (он сохранён на устройстве),
     * затем отправляет сообщение.
     */
    fun retryMessage(message: Message) {
        val localUuid = message.messageUuid
        if (!localUuid.startsWith(LOCAL_PREFIX)) return
        if (localUuid !in _uiState.value.failedMessages) return

        // Сбрасываем FAILED -> SENDING.
        _uiState.value = withStatuses(
            _uiState.value.copy(failedMessages = _uiState.value.failedMessages - localUuid)
        )

        // Текстовые сообщения живут в очереди — повторяем оттуда.
        if (outboxSender.pendingForChat(chatUuid).any { it.localMessageUuid == localUuid }) {
            outboxSender.retry(chatUuid, localUuid)
            return
        }

        viewModelScope.launch {
            val uploaded = mutableListOf<MessageAttachment>()
            for (attachment in message.attachments) {
                if (!attachment.attachmentUuid.isNullOrBlank()) {
                    uploaded += attachment
                    continue
                }
                val file = File(attachment.fileUrl)
                if (!file.exists()) {
                    retryFailed(localUuid, "Attachment file is no longer available")
                    return@launch
                }
                val prepared = PreparedAttachment(
                    file = file,
                    thumbnail = attachment.thumbnailUrl?.let(::File)?.takeIf { it.exists() },
                    name = attachment.fileName ?: file.name,
                    size = file.length(),
                    mimeType = attachment.mimeType ?: "application/octet-stream",
                    type = attachment.type ?: "FILE",
                    width = attachment.width,
                    height = attachment.height,
                    duration = attachment.duration
                )
                when (val result = messageRepository.uploadAttachment(chatUuid, prepared)) {
                    is Resource.Success -> {
                        result.data?.let { uploaded += it }
                        mediaFileReader.cleanup(prepared)
                    }
                    is Resource.Error -> {
                        retryFailed(localUuid, result.message ?: "Upload failed")
                        return@launch
                    }
                    is Resource.Loading -> Unit
                }
            }

            replaceMessageAttachments(localUuid, uploaded)
            messageRepository.sendMessage(
                chatUuid = chatUuid,
                text = message.text.orEmpty(),
                attachments = uploaded.mapNotNull { it.attachmentUuid },
                messageType = message.messageType ?: "TEXT",
                replyToMessageUuid = message.replyToMessageUuid
            ).collect { result ->
                when (result) {
                    is Resource.Success -> applyServerEcho(localUuid, result.data!!)
                    is Resource.Error -> retryFailed(localUuid, result.message ?: "Failed to send message")
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun retryFailed(localUuid: String, error: String) {
        markFailed(localUuid)
        viewModelScope.launch { _sendError.emit(error) }
    }

    /** Скачивает документ во временный файл для открытия системным приложением. */
    suspend fun downloadAttachment(
        attachment: MessageAttachment,
        onProgress: (Int) -> Unit = {}
    ): java.io.File? {
        val uuid = attachment.attachmentUuid ?: return null
        return messageRepository.downloadAttachment(uuid, attachment.fileName, onProgress)
    }

    suspend fun publishDownloadedAttachment(
        file: java.io.File,
        fileName: String?,
        mimeType: String?
    ): android.net.Uri? = messageRepository.publishDownloadedAttachment(file, fileName, mimeType)

    fun toggleReaction(message: Message, emoji: String) {
        viewModelScope.launch {
            val updated = messageRepository.toggleReaction(
                reducer.serverUuid(message.messageUuid), emoji
            )
            if (updated != null) {
                updateMessage(message.copy(reactions = updated))
            } else {
                _sendError.emit(appContext.getString(R.string.reaction_failed))
            }
        }
    }

    /** Статус сообщения для адаптера; null — у входящих и пока неизвестных. */
    fun deliveryStatus(messageUuid: String): MessageDeliveryStatus? =
        _uiState.value.statuses[messageUuid]

    /**
     * Кто поставил реакции — для блока «Детали». Нужен и на чужих сообщениях,
     * где списка получателей нет.
     */
    fun reactionAuthors(message: Message): List<Pair<String, ChatReceipt?>> {
        val uuids = message.reactions
            .flatMap { reaction -> reaction.users.mapNotNull { it.userUuid } }
            .distinct()
        if (uuids.isEmpty()) return emptyList()
        val byUuid = _uiState.value.receipts.associateBy { it.userUuid }
        // Реакцию мог поставить и я сам — моего receipt в списке участников нет.
        return uuids.map { uuid -> uuid to byUuid[uuid] }
    }

    /** Кто прочитал/получил сообщение — для шторки статуса. */
    fun participantStatuses(message: Message): List<ParticipantDeliveryStatus> =
        MessageStatusCalculator.participantStatuses(
            message = message,
            receipts = _uiState.value.receipts,
            myUserUuid = myUserUuid
        )

    /**
     * Отмечает прочитанным всё до указанного сообщения. Вызывается по мере
     * прокрутки — так счётчик непрочитанных уменьшается только на то, что
     * пользователь реально увидел.
     */
    fun markReadUpTo(messageUuid: String) {
        if (messageUuid.isBlank() || messageUuid.startsWith(LOCAL_PREFIX)) return
        if (messageUuid == lastMarkedReadUuid) return
        lastMarkedReadUuid = messageUuid
        viewModelScope.launch {
            if (!messageRepository.markMessagesAsRead(chatUuid, messageUuid)) {
                lastMarkedReadUuid = null
            }
        }
    }


    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var iteration = 0
            while (isActive) {
                val quality = networkMonitor.status.value.quality
                if (pollingPaused || quality == NetworkQuality.OFFLINE) {
                    delay(OFFLINE_POLL_INTERVAL_MS)
                    continue
                }

                // Полная страница — только когда показать ещё нечего или после
                // возврата на экран; в остальное время хватает дельты.
                val fullSync = lastServerCreatedAt == null || pendingFullSync
                if (fullSync) pendingFullSync = false
                val success = loadOnce(incremental = !fullSync)
                if (success && !fullSync && iteration % UPDATED_SYNC_EVERY == 0) {
                    // Раз в UPDATED_SYNC_EVERY опросов подтягиваем только
                    // изменённые сообщения: правки, закреп, снятые реакции.
                    loadUpdatedOnce()
                }
                iteration++

                val delayMs = when (quality) {
                    NetworkQuality.GOOD ->
                        if (success) POLL_INTERVAL_MS else ERROR_POLL_INTERVAL_MS
                    NetworkQuality.WEAK -> WEAK_POLL_INTERVAL_MS
                    NetworkQuality.OFFLINE -> OFFLINE_POLL_INTERVAL_MS
                }
                delay(delayMs)
            }
        }
    }

    private var lastServerCreatedAt: String? = null
    /**
     * До какого времени (UTC, формат сервера) уже сверены правки. Хранится
     * строкой: ровно в таком виде его ждёт эндпоинт /updated.
     */
    private var lastUpdatedCheckAt: String? = null
    /** Когда последний раз тянули квитанции — чтобы не делать это на каждом опросе. */
    private var lastReceiptsRefreshAt = 0L
    /** Нужна полная сверка на ближайшей итерации (возврат на экран, сеть). */
    private var pendingFullSync = false

    private fun rememberServerTime(messages: List<Message>) {
        val latest = messages.asSequence()
            .filter { it.messageId != null }
            .mapNotNull { it.createdAt }
            .maxOrNull()
        // +1 секунда: сервер сравнивает `created_at > since` без дробной части,
        // поэтому иначе последнее сообщение приходило бы в каждой дельте.
        // Сообщение, пришедшее в ту же секунду уже после запроса, подхватит
        // сверка /updated — она идёт раз в 15 секунд.
        LocalTimeFormatter.nextServerSecond(latest)?.let { lastServerCreatedAt = it }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    /** Отправить жалобу на сообщение. Возвращает true, если сервер её принял. */
    suspend fun reportMessage(messageUuid: String, reason: String): Boolean =
        messageRepository.reportMessage(reducer.serverUuid(messageUuid), reason)

    /** Заблокировать автора сообщения. */
    suspend fun blockUser(userUuid: String): Boolean =
        messageRepository.blockUser(userUuid)

    companion object {
        const val ARG_CHAT_UUID = "chat_uuid"
        private const val LOCAL_PREFIX = "local-"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val ERROR_POLL_INTERVAL_MS = 15_000L
        private const val WEAK_POLL_INTERVAL_MS = 15_000L
        private const val OFFLINE_POLL_INTERVAL_MS = 60_000L
        /** Каждая пятая итерация опроса (≈15 с) — сверка только изменённых. */
        private const val UPDATED_SYNC_EVERY = 5
        /**
         * Перекрытие окна сверки правок, секунды. Больше шага сверки, чтобы
         * не потерять строку, зафиксированную между запросами, но и не
         * пересылать одно и то же слишком много раз.
         */
        private const val UPDATED_OVERLAP_SECONDS = 30L
        /** Квитанции прочтения — не чаще раза в 30 секунд. */
        private const val RECEIPTS_REFRESH_INTERVAL_MS = 30_000L
        private const val PAGE_SIZE = 50
    }
}