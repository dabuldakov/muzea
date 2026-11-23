package com.example.muzea.ui.chat

import java.io.File
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.domain.model.MessageReceiptTime
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.data.repository.OutboxEvent
import com.example.muzea.data.repository.PendingAttachmentStore
import com.example.muzea.data.repository.OutboxSender
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.domain.chat.ChatMessageReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private var lastMarkedReadUuid: String? = null
    private var lastMarkedDeliveredUuid: String? = null
    /** Ошибку по сообщению показываем один раз, чтобы не спамить тостами. */
    private val notifiedFailures = mutableSetOf<String>()

    private val editor = ChatMessageEditor(
        chatUuid = chatUuid,
        scope = viewModelScope,
        repository = messageRepository,
        reducer = reducer,
        context = appContext,
        state = { _uiState.value },
        setState = { _uiState.value = it },
        withStatuses = ::withStatuses,
        emitError = { _sendError.emit(it) }
    )

    private val actions = ChatMessageActions(
        chatUuid = chatUuid,
        scope = viewModelScope,
        repository = messageRepository,
        reducer = reducer,
        context = appContext,
        state = { _uiState.value },
        setState = { _uiState.value = it },
        updateMessage = editor::update,
        emitError = { _sendError.emit(it) },
        emitEvent = { _events.emit(it) }
    )

    private val pendingAttachments = ChatPendingAttachmentController(
        chatUuid = chatUuid,
        scope = viewModelScope,
        repository = messageRepository,
        mediaFileReader = mediaFileReader,
        store = pendingStore,
        networkMonitor = networkMonitor,
        context = appContext,
        state = { _uiState.value },
        setState = { _uiState.value = it },
        emitError = { _sendError.emit(it) }
    )

    private val sender = ChatMessageSender(
        chatUuid = chatUuid,
        myUserUuid = myUserUuid,
        scope = viewModelScope,
        repository = messageRepository,
        outboxSender = outboxSender,
        reducer = reducer,
        mediaFileReader = mediaFileReader,
        context = appContext,
        state = { _uiState.value },
        setState = { _uiState.value = it },
        withStatuses = ::withStatuses,
        mergeMessages = ::mergeMessages,
        clearReply = ::clearReply,
        clearPendingAttachments = pendingAttachments::clearAll,
        onDelivered = ::markLatestAsDelivered,
        emitError = { _sendError.emit(it) }
    )

    private val receipts = ChatReceiptsController(
        chatUuid = chatUuid,
        repository = messageRepository,
        myUserUuid = myUserUuid,
        state = { _uiState.value },
        setState = { _uiState.value = it },
        withStatuses = ::withStatuses
    )

    private val polling = ChatPollingController(
        scope = viewModelScope,
        networkMonitor = networkMonitor,
        loadOnce = { incremental -> inbox.loadOnce(incremental) },
        loadUpdatedOnce = { inbox.loadUpdatedOnce() },
        onNetworkRestored = pendingAttachments::retryQueued
    )

    private lateinit var inbox: ChatInboxController

    init {
        inbox = ChatInboxController(
            chatUuid = chatUuid,
            scope = viewModelScope,
            repository = messageRepository,
            state = { _uiState.value },
            setState = { _uiState.value = it },
            mergeMessages = ::mergeMessages,
            cacheNewGifs = { messages -> sender.cacheNewGifs(messages) },
            refreshReceipts = { force -> receipts.refresh(force) },
            serverSince = { polling.lastServerCreatedAt },
            rememberServerTime = { messages -> polling.rememberServerTime(messages) },
            updatedSince = { polling.lastUpdatedCheckAt },
            rememberUpdatedCheck = { value -> polling.lastUpdatedCheckAt = value }
        )
        restorePendingMessages()
        pendingAttachments.restore()
        observeOutbox()
        polling.observeNetworkWakeup()
        // Опрос сам выполняет первый запрос, поэтому отдельный refresh() в init
        // был бы вторым обращением к серверу подряд при каждом входе в чат.
        polling.start()
        viewModelScope.launch { actions.refreshPinned() }
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
                        sender.applyServerEcho(event.item.localMessageUuid, event.message)
                    }
                    is OutboxEvent.Failed -> if (event.item.chatUuid == chatUuid) {
                        sender.markFailed(event.item.localMessageUuid)
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
            inbox.loadOnce()
            actions.refreshPinned()
        }
    }

    /** Догрузка истории вверх: подтягивает сообщения старше самого раннего в списке. */
    fun loadOlder() = inbox.loadOlder()

    /** Пауза опроса, когда экран не виден: не тратим сеть и батарею. */
    fun pausePolling() = polling.pause()

    fun resumePolling() = polling.resume()

    fun sendText(text: String, power: Int = 0) {
        val editing = _uiState.value.editing
        if (editing != null) {
            editor.submitEdit(editing, text)
            return
        }
        sender.sendText(text, power)
    }

    /** Добавляет выбранные в системном пикере файлы и сразу загружает их. */
    fun addAttachments(uris: List<android.net.Uri>) = pendingAttachments.add(uris)

    fun removePendingAttachment(id: String) = pendingAttachments.remove(id)

    fun retryPendingAttachment(id: String) = pendingAttachments.retry(id)

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
    fun startEditing(message: Message) = editor.startEditing(message)

    fun cancelEditing() = editor.cancelEditing()

    /** Мягко удаляет своё сообщение «для всех». */
    fun deleteMessage(message: Message) = editor.delete(message)

    /**
     * Пересылает сообщение в выбранный чат. Если пользователь добавил
     * комментарий, он отправляется в тот же чат отдельным сообщением.
     */
    fun forwardMessage(message: Message, targetChatUuid: String, comment: String? = null) =
        actions.forward(message, targetChatUuid, comment)

    /**
     * Пересылает пачку сообщений. Комментарий пользователя отправляется один
     * раз, после всех пересланных сообщений.
     */
    fun forwardMessages(messages: List<Message>, targetChatUuid: String, comment: String? = null) =
        actions.forwardMany(messages, targetChatUuid, comment)

    /** Закрепляет сообщение в текущем чате. */
    fun pinMessage(message: Message) = actions.pin(message)

    /** Открепляет сообщение. */
    fun unpinMessage(message: Message) = actions.unpin(message)

    fun sendGif(gif: Gif) = sender.sendGif(gif)

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
            it.messageId != null && !it.messageUuid.startsWith(LOCAL_MESSAGE_PREFIX)
        } ?: return
        if (latest.messageUuid == lastMarkedDeliveredUuid) return
        lastMarkedDeliveredUuid = latest.messageUuid
        viewModelScope.launch {
            if (!messageRepository.markMessagesAsDelivered(chatUuid, reducer.serverUuid(latest.messageUuid))) {
                lastMarkedDeliveredUuid = null
            }
        }
    }

    /** Свежие квитанции вне общего ритма — например, при открытии «Деталей». */
    suspend fun refreshReceiptsNow() = receipts.refreshNow()

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
    fun sendRecordedAttachment(prepared: PreparedAttachment, messageType: String) =
        sender.sendRecorded(prepared, messageType)

    /** Повторная отправка неудавшегося сообщения. */
    fun retryMessage(message: Message) = sender.retry(message)

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

    fun toggleReaction(message: Message, emoji: String) = actions.toggleReaction(message, emoji)

    /** Статус сообщения для адаптера; null — у входящих и пока неизвестных. */
    fun deliveryStatus(messageUuid: String): MessageDeliveryStatus? =
        receipts.deliveryStatus(messageUuid)

    /** Кто поставил реакции — для блока «Детали». */
    fun reactionAuthors(message: Message): List<Pair<String, ChatReceipt?>> =
        receipts.reactionAuthors(message)

    /** Загружает отметки сообщения; ошибку не показываем — время просто не появится. */
    suspend fun loadMessageReceiptTimes(message: Message) = receipts.loadTimes(message)

    /** Время конкретного участника по сообщению или null, если его ещё нет. */
    fun receiptTime(message: Message, userUuid: String): MessageReceiptTime? =
        receipts.time(message, userUuid)

    /** Кто прочитал/получил сообщение — для шторки статуса. */
    fun participantStatuses(message: Message): List<ParticipantDeliveryStatus> =
        receipts.participantStatuses(message)

    /**
     * Отмечает прочитанным всё до указанного сообщения. Вызывается по мере
     * прокрутки — так счётчик непрочитанных уменьшается только на то, что
     * пользователь реально увидел.
     */
    fun markReadUpTo(messageUuid: String) {
        if (messageUuid.isBlank() || messageUuid.startsWith(LOCAL_MESSAGE_PREFIX)) return
        if (messageUuid == lastMarkedReadUuid) return
        lastMarkedReadUuid = messageUuid
        viewModelScope.launch {
            if (!messageRepository.markMessagesAsRead(chatUuid, messageUuid)) {
                lastMarkedReadUuid = null
            }
        }
    }


    override fun onCleared() {
        polling.cancel()
        super.onCleared()
    }

    /** Отправить жалобу на сообщение. Возвращает true, если сервер её принял. */
    suspend fun reportMessage(messageUuid: String, reason: String): Boolean =
        actions.report(messageUuid, reason)

    /** Заблокировать автора сообщения. */
    suspend fun blockUser(userUuid: String): Boolean = actions.block(userUuid)

    companion object {
        const val ARG_CHAT_UUID = "chat_uuid"
    }
}