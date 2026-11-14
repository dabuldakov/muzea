package com.example.muzea.domain.repository

import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.Presence
import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Контракты чат-фичи на доменных моделях. Реализации живут в data-слое,
 * привязка — через Hilt. UI и ViewModel зависят только от этих интерфейсов.
 */

interface ChatRepository {
    suspend fun loadChats(): Flow<Resource<List<Chat>>>
    fun cachedChats(): List<Chat>
    suspend fun loadChatParticipants(chatUuid: String): Flow<Resource<List<ChatParticipant>>>
    suspend fun createPrivateChat(userUuid: String): Flow<Resource<Chat>>
    suspend fun findPrivateChatWith(userUuid: String): Chat?
    suspend fun createGroupChat(title: String, memberUuids: List<String>): Flow<Resource<Chat>>
    suspend fun addGroupParticipants(chatUuid: String, memberUuids: List<String>): Flow<Resource<Unit>>
}

interface MessageRepository {
    suspend fun loadMessages(chatUuid: String): Flow<Resource<List<Message>>>

    /** Догрузка истории: сообщения старше [beforeMessageUuid]. */
    suspend fun loadMessagesAfter(chatUuid: String, timestamp: String): Flow<Resource<List<Message>>>

    /** Только изменённые сообщения после времени: правки и удаления без полной страницы. */
    suspend fun loadUpdatedMessages(chatUuid: String, timestamp: String): List<Message>?
    suspend fun loadMessagesBefore(
        chatUuid: String,
        beforeMessageUuid: String,
        limit: Int
    ): Flow<Resource<List<Message>>>

    fun cachedMessages(chatUuid: String): List<Message>
    suspend fun sendMessage(
        chatUuid: String,
        text: String,
        attachments: List<String> = emptyList(),
        messageType: String = "TEXT",
        replyToMessageUuid: String? = null,
        clientMessageId: String? = null
    ): Flow<Resource<Message>>

    /** Отправка с силой «заряда» 0..100. */
    suspend fun sendMessage(
        chatUuid: String,
        text: String,
        attachments: List<String>,
        messageType: String,
        replyToMessageUuid: String?,
        clientMessageId: String?,
        power: Int
    ): Flow<Resource<Message>> = sendMessage(
        chatUuid, text, attachments, messageType, replyToMessageUuid, clientMessageId
    )
    suspend fun sendGif(
        chatUuid: String,
        gif: Gif,
        replyToMessageUuid: String? = null
    ): Flow<Resource<Message>>

    /** Редактирует своё текстовое сообщение; возвращает обновлённое сообщение. */
    suspend fun editMessage(messageUuid: String, text: String): Flow<Resource<Message>>

    /** Мягко удаляет своё сообщение «для всех». true — сервер принял удаление. */
    suspend fun deleteMessage(messageUuid: String): Boolean

    /** Пересылает сообщение в другой чат; возвращает созданное сообщение. */
    suspend fun forwardMessage(
        messageUuid: String,
        targetChatUuid: String
    ): Flow<Resource<Message>>

    /** Закрепляет сообщение в чате. true — сервер принял. */
    suspend fun pinMessage(chatUuid: String, messageUuid: String): Boolean

    /** Открепляет сообщение. true — сервер принял. */
    suspend fun unpinMessage(chatUuid: String, messageUuid: String): Boolean

    /** Закреплённые сообщения чата. */
    suspend fun loadPinned(chatUuid: String): List<Message>

    /** Загружает файл в чат; [attachmentUuid] потом передаётся в сообщение. */
    suspend fun uploadAttachment(
        chatUuid: String,
        prepared: com.example.muzea.utils.PreparedAttachment,
        onProgress: (Int) -> Unit = {}
    ): Resource<MessageAttachment>

    /** Скачивает вложение в cacheDir; null — если не удалось. */
    suspend fun downloadAttachment(attachmentUuid: String, fileName: String?): java.io.File?

    /** То же, но с прогрессом 0..100 (для индикатора в UI). */
    suspend fun downloadAttachment(
        attachmentUuid: String,
        fileName: String?,
        onProgress: (Int) -> Unit
    ): java.io.File? = downloadAttachment(attachmentUuid, fileName)

    /** Копирует файл в «Загрузки» и возвращает content-uri (null — не вышло). */
    suspend fun publishDownloadedAttachment(
        file: java.io.File,
        fileName: String?,
        mimeType: String?
    ): android.net.Uri? = null

    /** Ставит/снимает реакцию и возвращает обновлённую сводку; null — ошибка. */
    suspend fun toggleReaction(
        messageUuid: String,
        emoji: String
    ): List<com.example.muzea.domain.model.MessageReaction>? = null

    suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean

    /** Подтверждает доставку всей ленты до [upToMessageUuid] на это устройство. */
    suspend fun markMessagesAsDelivered(chatUuid: String, upToMessageUuid: String): Boolean

    /** Квитанции участников чата; null — если загрузить не удалось. */
    suspend fun loadChatReceipts(chatUuid: String): List<com.example.muzea.domain.model.ChatReceipt>?

    /** Точные отметки одного сообщения по участникам; null — если не удалось. */
    suspend fun loadMessageReceiptTimes(
        chatUuid: String,
        messageUuid: String
    ): List<com.example.muzea.domain.model.MessageReceiptTime>?

    /** Живое обновление кэша гифок: добавить гифку из переписки без сети. */
    fun cacheSentGif(chatUuid: String, gif: Gif, mine: Boolean)

    /** Жалоба на сообщение (UGC). true — сервер принял жалобу. */
    suspend fun reportMessage(messageUuid: String, reason: String): Boolean

    /** Блокировка пользователя. true — пользователь заблокирован. */
    suspend fun blockUser(userUuid: String): Boolean
}

interface GifRepository {
    /** Поиск у провайдера (пустой запрос — trending). */
    suspend fun search(query: String, page: Int, size: Int): List<Gif>
    /** «Мои» использованные гифки (с нашего API). */
    suspend fun recent(page: Int, size: Int): List<Gif>
    /** Гифки конкретного чата (с нашего API). */
    suspend fun chatGifs(chatUuid: String, page: Int, size: Int): List<Gif>
}

interface ContactRepository {
    suspend fun loadContacts(): Flow<Resource<List<Contact>>>
    suspend fun addContactByUsername(username: String): Flow<Resource<Contact>>
    suspend fun loadPresence(userUuids: List<String>): Map<String, Presence>
}

interface AvatarRepository {
    suspend fun loadAvatar(): Flow<Resource<Avatar>>
    suspend fun uploadAvatar(file: File, mimeType: String): Flow<Resource<Avatar>>
    suspend fun deleteAvatar(): Flow<Resource<Avatar>>
    suspend fun uploadChatAvatar(
        chatUuid: String,
        file: File,
        mimeType: String
    ): Flow<Resource<String>>
}

interface ChatSessionRepository {
    suspend fun deleteAccount(): Boolean
    suspend fun sendHeartbeat(): Boolean
    suspend fun getTotalUnreadCount(): Flow<Resource<Long>>
}
