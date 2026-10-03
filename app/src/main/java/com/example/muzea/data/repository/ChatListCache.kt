package com.example.muzea.data.repository

import com.example.muzea.domain.model.Chat

/**
 * Кэш списка чатов в памяти процесса.
 *
 * Аналог [ChatMessagesCache], но для списка диалогов. Нужен потому, что и
 * репозиторий, и ViewModel списка создаются заново при каждом показе экрана:
 * без общего объекта список каждый раз запрашивался бы с сервера и экран
 * мигал бы индикатором загрузки. Здесь список отдаётся мгновенно, а актуальные
 * данные догружаются сетью в фоне.
 *
 * Хранится не больше [MAX_CHATS] диалогов, чтобы память не росла бесконечно.
 */
object ChatListCache {

    private const val MAX_CHATS = 200

    private val chats = LinkedHashMap<String, Chat>()

    @Synchronized
    fun get(): List<Chat> = chats.values.toList()

    @Synchronized
    fun has(): Boolean = chats.isNotEmpty()

    @Synchronized
    fun put(list: List<Chat>) {
        chats.clear()
        for (chat in list) {
            if (chat.chatUuid.isNotBlank()) {
                chats[chat.chatUuid] = chat
            }
        }
        while (chats.size > MAX_CHATS) {
            val oldest = chats.keys.firstOrNull() ?: break
            chats.remove(oldest)
        }
    }

    @Synchronized
    fun clear() {
        chats.clear()
    }
}