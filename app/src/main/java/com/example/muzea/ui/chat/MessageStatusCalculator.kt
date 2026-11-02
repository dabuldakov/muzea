package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageDeliveryStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Статус конкретного получателя для выбранного сообщения. */
data class ParticipantDeliveryStatus(
    val receipt: ChatReceipt,
    val status: MessageDeliveryStatus
)

/**
 * Расчёт галочек по watermark-ам участников.
 *
 * Отдельный чистый объект, чтобы правила «доставлено/прочитано всеми» можно было
 * покрыть unit-тестами без ViewModel и Android.
 */
object MessageStatusCalculator {

    fun statusOf(
        message: Message,
        receipts: List<ChatReceipt>,
        myUserUuid: String?,
        failedMessages: Set<String> = emptySet(),
        receiptsLoaded: Boolean = true
    ): MessageDeliveryStatus? {
        if (myUserUuid == null || message.senderUuid != myUserUuid) return null

        // Локальное сообщение ещё не имеет серверного id.
        if (message.messageId == null) {
            return if (message.messageUuid in failedMessages) {
                MessageDeliveryStatus.FAILED
            } else {
                MessageDeliveryStatus.SENDING
            }
        }

        // Квитанции ещё не загружены: не показываем ложное «прочитано».
        if (!receiptsLoaded) {
            return MessageDeliveryStatus.SENT
        }

        // Участник, вступивший позже, не должен удерживать статус в SENT.
        val recipients = receipts.filter {
            isRecipient(it, message, myUserUuid)
        }
        if (recipients.isEmpty()) {
            // Чат с самим собой: сообщение сразу считается прочитанным.
            return MessageDeliveryStatus.READ
        }

        val messageId = message.messageId
        val readByAll = recipients.all { receipt ->
            receipt.lastReadMessageId != null && receipt.lastReadMessageId >= messageId
        }
        if (readByAll) return MessageDeliveryStatus.READ

        val deliveredToAll = recipients.all { receipt ->
            receipt.lastDeliveredMessageId != null && receipt.lastDeliveredMessageId >= messageId
        }
        if (deliveredToAll) return MessageDeliveryStatus.DELIVERED

        return MessageDeliveryStatus.SENT
    }

    /** Статусы всех своих сообщений: uuid сообщения → галочка. */
    fun statuses(
        messages: List<Message>,
        receipts: List<ChatReceipt>,
        myUserUuid: String?,
        failedMessages: Set<String> = emptySet(),
        receiptsLoaded: Boolean = true
    ): Map<String, MessageDeliveryStatus> = buildMap {
        messages.forEach { message ->
            statusOf(message, receipts, myUserUuid, failedMessages, receiptsLoaded)?.let {
                put(message.messageUuid, it)
            }
        }
    }

    /**
     * Кто прочитал/получил конкретное сообщение — для шторки «Статус сообщения».
     * Участники, вступившие после сообщения, не показываются.
     */
    fun participantStatuses(
        message: Message,
        receipts: List<ChatReceipt>,
        myUserUuid: String?
    ): List<ParticipantDeliveryStatus> {
        val messageId = message.messageId ?: return emptyList()
        return receipts
            .filter { isRecipient(it, message, myUserUuid) }
            .map { receipt ->
                val status = when {
                    receipt.lastReadMessageId != null && receipt.lastReadMessageId >= messageId ->
                        MessageDeliveryStatus.READ
                    receipt.lastDeliveredMessageId != null &&
                        receipt.lastDeliveredMessageId >= messageId ->
                        MessageDeliveryStatus.DELIVERED
                    else -> MessageDeliveryStatus.SENT
                }
                ParticipantDeliveryStatus(receipt, status)
            }
            .sortedBy { row ->
                when (row.status) {
                    MessageDeliveryStatus.READ -> 0
                    MessageDeliveryStatus.DELIVERED -> 1
                    else -> 2
                }
            }
    }

    private fun isRecipient(
        receipt: ChatReceipt,
        message: Message,
        myUserUuid: String?
    ): Boolean {
        if (receipt.userUuid == myUserUuid) return false
        val joinedAt = parseMillis(receipt.joinedAt) ?: return true
        val createdAt = parseMillis(message.createdAt) ?: return true
        return joinedAt <= createdAt
    }

    private fun parseMillis(iso: String?): Long? {
        if (iso.isNullOrEmpty()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(iso.substring(0, 19))?.time
        } catch (e: Exception) {
            null
        }
    }
}
