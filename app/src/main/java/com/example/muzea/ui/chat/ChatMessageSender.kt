package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.OutboxItem
import com.example.muzea.data.repository.OutboxSender
import com.example.muzea.domain.chat.ChatMessageReducer
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.PreparedAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Исходящие сообщения: текст через durable-очередь, GIF, голосовые/видео,
 * повтор неудачных и применение серверного эха. Состояние остаётся в
 * [ChatConversationUiState], входящие сообщения сливает ViewModel.
 */
internal class ChatMessageSender(
    private val chatUuid: String,
    private val myUserUuid: String?,
    private val scope: CoroutineScope,
    private val repository: MessageRepository,
    private val outboxSender: OutboxSender,
    private val reducer: ChatMessageReducer,
    private val mediaFileReader: MediaFileReader,
    private val context: Context,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val withStatuses: (ChatConversationUiState) -> ChatConversationUiState,
    private val mergeMessages: (List<Message>) -> Unit,
    private val clearReply: () -> Unit,
    private val clearPendingAttachments: () -> Unit,
    private val onDelivered: () -> Unit,
    private val emitError: suspend (String) -> Unit
) {

    fun sendText(text: String, power: Int = 0) {
        val trimmed = text.trim()
        val current = state()
        val attachments = current.pendingAttachments
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (attachments.any { !it.isUploaded }) {
            scope.launch { emitError(context.getString(R.string.attachment_wait_upload)) }
            return
        }

        val uploaded = attachments.mapNotNull { it.uploaded }
        val type = messageTypeFor(uploaded)
        val replyToUuid = current.replyTo?.messageUuid?.let { reducer.serverUuid(it) }
        val optimistic = Message(
            messageUuid = LOCAL_MESSAGE_PREFIX + System.currentTimeMillis(),
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
                clientMessageId = UUID.randomUUID().toString(),
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

    fun sendGif(gif: Gif) {
        val replyToUuid = state().replyTo?.messageUuid?.let { reducer.serverUuid(it) }
        val optimistic = Message(
            messageUuid = LOCAL_MESSAGE_PREFIX + System.currentTimeMillis(),
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
        repository.cacheSentGif(chatUuid, gif, mine = true)

        scope.launch {
            repository.sendGif(chatUuid, gif, replyToMessageUuid = replyToUuid).collect { result ->
                when (result) {
                    is Resource.Success -> applyServerEcho(optimistic.messageUuid, result.data!!)
                    is Resource.Error -> {
                        markFailed(optimistic.messageUuid)
                        emitError(result.message ?: "Failed to send GIF")
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    /**
     * Загружает только что записанное голосовое/видео-сообщение и отправляет его.
     * Тип VOICE/VIDEO_NOTE передаётся явно — по нему UI рисует волну/кружок.
     */
    fun sendRecorded(prepared: PreparedAttachment, messageType: String) {
        val localUuid = LOCAL_MESSAGE_PREFIX + System.currentTimeMillis()
        val replyToUuid = state().replyTo?.messageUuid?.let { reducer.serverUuid(it) }
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

        scope.launch {
            when (val uploaded = repository.uploadAttachment(chatUuid, prepared)) {
                is Resource.Success -> {
                    val attachment = uploaded.data ?: return@launch
                    replaceLocalAttachment(localUuid, attachment)
                    mediaFileReader.cleanup(prepared)

                    repository.sendMessage(
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
                                emitError(result.message ?: "Failed to send message")
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
                is Resource.Error -> {
                    // Файл не удаляем: запись остаётся в пузыре и её можно
                    // перезаписать/отправить позже, когда появится сеть.
                    markFailed(localUuid)
                    emitError(uploaded.message ?: "Upload failed")
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Повторная отправка неудавшегося сообщения. Для локальных голосовых и
     * видео сначала повторно загружает файл (он сохранён на устройстве),
     * затем отправляет сообщение.
     */
    fun retry(message: Message) {
        val localUuid = message.messageUuid
        if (!localUuid.startsWith(LOCAL_MESSAGE_PREFIX)) return
        if (localUuid !in state().failedMessages) return

        // Сбрасываем FAILED -> SENDING.
        setState(withStatuses(state().copy(failedMessages = state().failedMessages - localUuid)))

        // Текстовые сообщения живут в очереди — повторяем оттуда.
        if (outboxSender.pendingForChat(chatUuid).any { it.localMessageUuid == localUuid }) {
            outboxSender.retry(chatUuid, localUuid)
            return
        }

        scope.launch {
            val uploaded = uploadForRetry(message) ?: return@launch
            replaceMessageAttachments(localUuid, uploaded)
            repository.sendMessage(
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

    /**
     * Обновляет локальный пузырь данными серверного эха, не меняя его
     * идентификатор в списке. Раньше локальное сообщение удалялось, а серверное
     * вставлялось заново: DiffUtil считал строку другой и перерисовывал её —
     * при отправке список мигал. Теперь это обычное изменение содержимого.
     */
    fun applyServerEcho(localUuid: String, serverMessage: Message) {
        val current = state()
        setState(
            withStatuses(
                current.copy(
                    messages = reducer.applyServerEcho(current.messages, localUuid, serverMessage),
                    failedMessages = current.failedMessages - localUuid
                )
            )
        )
        onDelivered()
    }

    fun markFailed(localUuid: String) {
        setState(withStatuses(state().copy(failedMessages = state().failedMessages + localUuid)))
    }

    /**
     * Новая гифка в переписке (в т.ч. от другого участника) — добавляем её в
     * кэш «Этот чат»/«Мои» из уже полученного сообщения, без обращения к сети.
     * Никакого сброса кэша: список просто пополняется сверху.
     */
    fun cacheNewGifs(incoming: List<Message>) {
        if (incoming.isEmpty()) return
        val known = state().messages.mapTo(HashSet()) { it.messageUuid }
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
            repository.cacheSentGif(chatUuid, gif, mine = message.senderUuid == myUserUuid)
        }
    }

    private fun messageTypeFor(attachments: List<MessageAttachment>): String {
        if (attachments.isEmpty()) return "TEXT"
        if (attachments.all { it.type == "IMAGE" }) return "IMAGE"
        if (attachments.all { it.type == "AUDIO" }) return "AUDIO"
        if (attachments.size == 1 && attachments.single().type == "VIDEO") return "VIDEO"
        return "FILE"
    }

    private fun Message.isGifMessage(): Boolean =
        messageType?.equals("GIF", ignoreCase = true) == true ||
            attachments.any { it.type?.equals("GIF", ignoreCase = true) == true }

    /** Догружает файлы локального сообщения; null — если что-то не удалось. */
    private suspend fun uploadForRetry(message: Message): List<MessageAttachment>? {
        val localUuid = message.messageUuid
        val uploaded = mutableListOf<MessageAttachment>()
        for (attachment in message.attachments) {
            if (!attachment.attachmentUuid.isNullOrBlank()) {
                uploaded += attachment
                continue
            }
            val file = File(attachment.fileUrl)
            if (!file.exists()) {
                retryFailed(localUuid, "Attachment file is no longer available")
                return null
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
            when (val result = repository.uploadAttachment(chatUuid, prepared)) {
                is Resource.Success -> {
                    result.data?.let { uploaded += it }
                    mediaFileReader.cleanup(prepared)
                }
                is Resource.Error -> {
                    retryFailed(localUuid, result.message ?: "Upload failed")
                    return null
                }
                is Resource.Loading -> Unit
            }
        }
        return uploaded
    }

    private fun replaceLocalAttachment(localUuid: String, attachment: MessageAttachment) {
        replaceMessageAttachments(localUuid, listOf(attachment))
    }

    private fun replaceMessageAttachments(localUuid: String, attachments: List<MessageAttachment>) {
        val current = state()
        setState(
            withStatuses(
                current.copy(
                    messages = current.messages.map { message ->
                        if (message.messageUuid == localUuid) {
                            message.copy(attachments = attachments)
                        } else {
                            message
                        }
                    }
                )
            )
        )
    }

    private fun retryFailed(localUuid: String, error: String) {
        markFailed(localUuid)
        scope.launch { emitError(error) }
    }
}
