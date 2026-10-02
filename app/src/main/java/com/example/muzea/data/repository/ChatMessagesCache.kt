package com.example.muzea.data.repository

import com.example.muzea.data.model.MessageResponse

/**
 * Кэш сообщений по чатам в памяти процесса.
 *
 * [ChatRepository] создаётся заново на каждом экране, поэтому кэш внутри
 * репозитория не пережил бы переоткрытие чата. Общий объект позволяет
 * мгновенно показать переписку при повторном входе, пока свежие сообщения
 * догружаются в фоне. Хранит не больше [MAX_CHATS] последних чатов, чтобы
 * память не росла бесконечно.
 */
object ChatMessagesCache {

    private const val MAX_CHATS = 50

    private val messages = LinkedHashMap<String, List<MessageResponse>>()

    @Synchronized
    fun get(chatUuid: String): List<MessageResponse> = messages[chatUuid].orEmpty()

    @Synchronized
    fun has(chatUuid: String): Boolean = messages.containsKey(chatUuid)

    @Synchronized
    fun put(chatUuid: String, list: List<MessageResponse>) {
        messages.remove(chatUuid)
        messages[chatUuid] = list
        while (messages.size > MAX_CHATS) {
            val oldest = messages.keys.firstOrNull() ?: break
            messages.remove(oldest)
        }
    }

    @Synchronized
    fun clear() {
        messages.clear()
    }
}
