package com.example.muzea.data.repository

import com.example.muzea.domain.model.Message

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

    private val messages = LinkedHashMap<String, List<Message>>()

    @Synchronized
    fun get(chatUuid: String): List<Message> = messages[chatUuid].orEmpty()

    @Synchronized
    fun has(chatUuid: String): Boolean = messages.containsKey(chatUuid)

    @Synchronized
    fun put(chatUuid: String, list: List<Message>) {
        messages.remove(chatUuid)
        messages[chatUuid] = list
        while (messages.size > MAX_CHATS) {
            val oldest = messages.keys.firstOrNull() ?: break
            messages.remove(oldest)
        }
    }

    /**
     * Дополняет кэш чата более старыми сообщениями (догрузка истории вверх).
     * Дубликаты по uuid отбрасываются, старые кладутся перед текущими, чтобы
     * при повторном входе вся загруженная история была на месте.
     */
    @Synchronized
    fun prependOlder(chatUuid: String, older: List<Message>) {
        val existing = messages[chatUuid].orEmpty()
        if (older.isEmpty()) return
        val known = existing.mapTo(HashSet()) { it.messageUuid }
        val merged = older.filter { it.messageUuid !in known } + existing
        put(chatUuid, merged)
    }

    @Synchronized
    fun clear() {
        messages.clear()
    }
}
