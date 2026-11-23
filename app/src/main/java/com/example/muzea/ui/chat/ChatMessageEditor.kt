package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.domain.chat.ChatMessageReducer
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessagePreview
import com.example.muzea.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Редактирование и удаление сообщений: режим правки, обновление пузыря после
 * ответа сервера, откат превью чата. Состояние остаётся в
 * [ChatConversationUiState].
 */
internal class ChatMessageEditor(
    private val chatUuid: String,
    private val scope: CoroutineScope,
    private val repository: MessageRepository,
    private val reducer: ChatMessageReducer,
    private val context: Context,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val withStatuses: (ChatConversationUiState) -> ChatConversationUiState,
    private val emitError: suspend (String) -> Unit
) {

    /** Включает режим редактирования; режим ответа при этом сбрасывается. */
    fun startEditing(message: Message) {
        setState(state().copy(editing = message, replyTo = null))
    }

    fun cancelEditing() {
        clearEditing()
    }

    fun submitEdit(target: Message, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            scope.launch { emitError(context.getString(R.string.error_message_required)) }
            return
        }
        clearEditing()
        if (trimmed == target.text) return

        scope.launch {
            repository.editMessage(reducer.serverUuid(target.messageUuid), trimmed).collect { result ->
                when (result) {
                    is Resource.Success -> update(result.data!!)
                    is Resource.Error ->
                        emitError(result.message ?: "Failed to edit message")
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    /** Применяет серверную версию сообщения, сохраняя локальный uuid пузыря. */
    fun update(updated: Message) {
        // Сервер возвращает сообщение с реальным uuid; в списке наше сообщение
        // может храниться под локальным uuid — приводим к нему, иначе правка
        // не найдёт нужный пузырь.
        val displayUuid = reducer.localUuidFor(updated.messageUuid) ?: updated.messageUuid
        val reconciled = updated.copy(messageUuid = displayUuid)
        // Сервер может вернуть сообщение без вложений (например, при правке
        // подписи к фото). Не теряем уже показанную картинку.
        val current = state()
        val existing = current.messages.firstOrNull {
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
        val messages = reducer.updateMessage(current.messages, merged)
        setState(withStatuses(current.copy(messages = messages)))
        ChatMessagesCache.put(chatUuid, reducer.toServerIdentities(messages))
        // Если правили последнее сообщение — сразу обновляем превью в списке чатов.
        if (messages.lastOrNull()?.messageUuid == merged.messageUuid) {
            ChatListCache.updateLastMessage(chatUuid, previewOf(merged))
        }
    }

    /** Мягко удаляет своё сообщение «для всех». */
    fun delete(message: Message) {
        scope.launch {
            val ok = repository.deleteMessage(reducer.serverUuid(message.messageUuid))
            if (ok) {
                removeLocally(message.messageUuid)
                clearComposerFor(message.messageUuid)
            } else {
                emitError(context.getString(R.string.delete_message_failed))
            }
        }
    }

    private fun clearEditing() {
        if (state().editing != null) {
            setState(state().copy(editing = null))
        }
    }

    /**
     * Удалённое сообщение не показываем пузырём-заглушкой: убираем его из
     * списка, а RecyclerView анимирует исчезновение (scale-to-point). Превью
     * чата откатывается на предыдущее сообщение.
     */
    private fun removeLocally(messageUuid: String) {
        val remaining = state().messages.filterNot { it.messageUuid == messageUuid }
        setState(withStatuses(state().copy(messages = remaining)))
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
        text = replyPreviewText(context, message),
        senderId = message.senderId,
        senderName = message.senderName,
        createdAt = message.createdAt
    )

    /** Сбрасывает «ответ»/«редактирование», если они ссылаются на это сообщение. */
    private fun clearComposerFor(messageUuid: String) {
        val current = state()
        val editingThis = current.editing?.messageUuid == messageUuid
        val replyThis = current.replyTo?.messageUuid == messageUuid
        if (editingThis || replyThis) {
            setState(
                current.copy(
                    editing = if (editingThis) null else current.editing,
                    replyTo = if (replyThis) null else current.replyTo
                )
            )
        }
    }
}
