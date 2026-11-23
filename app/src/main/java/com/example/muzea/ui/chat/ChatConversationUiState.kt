package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.utils.PreparedAttachment

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

/** Единое состояние экрана переписки. */
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
