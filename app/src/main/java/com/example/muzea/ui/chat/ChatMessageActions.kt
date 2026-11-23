package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.domain.chat.ChatMessageReducer
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Действия над сообщениями: пересылка, закрепление, реакции, жалобы и
 * блокировка. Правка/удаление — в [ChatMessageEditor].
 */
internal class ChatMessageActions(
    private val chatUuid: String,
    private val scope: CoroutineScope,
    private val repository: MessageRepository,
    private val reducer: ChatMessageReducer,
    private val context: Context,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val updateMessage: (Message) -> Unit,
    private val emitError: suspend (String) -> Unit,
    private val emitEvent: suspend (String) -> Unit
) {

    /** Обновляет список закреплённых для панели сверху. */
    suspend fun refreshPinned() {
        setState(state().copy(pinnedMessages = repository.loadPinned(chatUuid)))
    }

    /**
     * Пересылает сообщение в выбранный чат. Если пользователь добавил
     * комментарий, он отправляется в тот же чат отдельным сообщением.
     */
    fun forward(message: Message, targetChatUuid: String, comment: String? = null) {
        forwardMany(listOf(message), targetChatUuid, comment)
    }

    /**
     * Пересылает пачку сообщений. Комментарий пользователя отправляется один
     * раз, после всех пересланных сообщений.
     */
    fun forwardMany(messages: List<Message>, targetChatUuid: String, comment: String? = null) {
        if (messages.isEmpty()) return
        scope.launch {
            var forwarded = false
            for (message in messages) {
                repository.forwardMessage(reducer.serverUuid(message.messageUuid), targetChatUuid)
                    .collect { result ->
                        when (result) {
                            is Resource.Success -> forwarded = true
                            is Resource.Error ->
                                emitError(context.getString(R.string.forward_message_failed))
                            is Resource.Loading -> Unit
                        }
                    }
            }
            if (!forwarded) return@launch
            if (!comment.isNullOrBlank()) {
                repository.sendMessage(
                    targetChatUuid, comment, emptyList(), "TEXT", null
                ).collect { result ->
                    if (result is Resource.Error) {
                        emitError(
                            result.message ?: context.getString(R.string.forward_message_failed)
                        )
                    }
                }
            }
            emitEvent(context.getString(R.string.message_forwarded))
        }
    }

    /** Закрепляет сообщение в текущем чате. */
    fun pin(message: Message) {
        scope.launch {
            val ok = repository.pinMessage(chatUuid, reducer.serverUuid(message.messageUuid))
            if (ok) {
                updateMessage(message.copy(isPinned = true))
                refreshPinned()
            } else {
                emitError(context.getString(R.string.pin_message_failed))
            }
        }
    }

    /** Открепляет сообщение. */
    fun unpin(message: Message) {
        scope.launch {
            val ok = repository.unpinMessage(chatUuid, reducer.serverUuid(message.messageUuid))
            if (ok) {
                updateMessage(message.copy(isPinned = false))
                refreshPinned()
            } else {
                emitError(context.getString(R.string.unpin_message_failed))
            }
        }
    }

    fun toggleReaction(message: Message, emoji: String) {
        scope.launch {
            val updated = repository.toggleReaction(
                reducer.serverUuid(message.messageUuid), emoji
            )
            if (updated != null) {
                updateMessage(message.copy(reactions = updated))
            } else {
                emitError(context.getString(R.string.reaction_failed))
            }
        }
    }

    /** Отправить жалобу на сообщение. Возвращает true, если сервер её принял. */
    suspend fun report(messageUuid: String, reason: String): Boolean =
        repository.reportMessage(reducer.serverUuid(messageUuid), reason)

    /** Заблокировать автора сообщения. */
    suspend fun block(userUuid: String): Boolean =
        repository.blockUser(userUuid)
}
