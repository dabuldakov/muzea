package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.domain.model.MessageReceiptTime
import com.example.muzea.domain.repository.MessageRepository

/**
 * Квитанции и статусы: загрузка «кто прочитал», точное время по сообщению и
 * статус для галочки. Состояние остаётся в [ChatConversationUiState].
 */
internal class ChatReceiptsController(
    private val chatUuid: String,
    private val repository: MessageRepository,
    private val myUserUuid: String?,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val withStatuses: (ChatConversationUiState) -> ChatConversationUiState
) {

    private var lastRefreshAt = 0L
    private val receiptTimes = mutableMapOf<String, Map<String, MessageReceiptTime>>()

    /**
     * Подтягивает квитанции участников, но не чаще раза в 30 секунд:
     * галочки не обязаны обновляться каждые 3 секунды, а каждый такой запрос —
     * это ~1,25 КБ (в большой группе — десятки килобайт).
     * Ошибку не показываем: галочки просто останутся на прошлом значении.
     */
    suspend fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < REFRESH_INTERVAL_MS) return
        val receipts = repository.loadChatReceipts(chatUuid) ?: return
        lastRefreshAt = now
        setState(withStatuses(state().copy(receipts = receipts, receiptsLoaded = true)))
    }

    /** Свежие квитанции вне общего ритма — например, при открытии «Деталей». */
    suspend fun refreshNow() {
        refresh(force = true)
    }

    /** Статус сообщения для адаптера; null — у входящих и пока неизвестных. */
    fun deliveryStatus(messageUuid: String): MessageDeliveryStatus? =
        state().statuses[messageUuid]

    /**
     * Кто поставил реакции — для блока «Детали». Нужен и на чужих сообщениях,
     * где списка получателей нет.
     */
    fun reactionAuthors(message: Message): List<Pair<String, ChatReceipt?>> {
        val uuids = message.reactions
            .flatMap { reaction -> reaction.users.mapNotNull { it.userUuid } }
            .distinct()
        if (uuids.isEmpty()) return emptyList()
        val byUuid = state().receipts.associateBy { it.userUuid }
        // Реакцию мог поставить и я сам — моего receipt в списке участников нет.
        return uuids.map { uuid -> uuid to byUuid[uuid] }
    }

    /** Загружает отметки сообщения; ошибку не показываем — время просто не появится. */
    suspend fun loadTimes(message: Message) {
        if (message.messageId == null) return
        val times = repository.loadMessageReceiptTimes(chatUuid, message.messageUuid)
            ?: return
        receiptTimes[message.messageUuid] = times
            .mapNotNull { time -> time.userUuid?.let { it to time } }
            .toMap()
    }

    /** Время конкретного участника по сообщению или null, если его ещё нет. */
    fun time(message: Message, userUuid: String): MessageReceiptTime? =
        receiptTimes[message.messageUuid]?.get(userUuid)

    /** Кто прочитал/получил сообщение — для шторки статуса. */
    fun participantStatuses(message: Message): List<ParticipantDeliveryStatus> =
        MessageStatusCalculator.participantStatuses(
            message = message,
            receipts = state().receipts,
            myUserUuid = myUserUuid
        )

    private companion object {
        /** Квитанции прочтения — не чаще раза в 30 секунд. */
        const val REFRESH_INTERVAL_MS = 30_000L
    }
}
