package com.example.muzea.data.repository

import com.example.muzea.data.model.ChatResponse

/**
 * Кэш соответствия «пользователь → уже существующий приватный чат».
 *
 * Поиск переписки требует обхода всех приватных чатов и запроса их участников,
 * что при каждом тапе по контакту дорого. Состав участников приватного чата со
 * временем не меняется, поэтому однажды найденную пару можно запомнить на
 * сессию и потом открывать чат мгновенно, без обращения к сети.
 *
 * [MAX_ENTRIES] ограничивает память.
 */
object PrivateChatCache {

    private const val MAX_ENTRIES = 200

    private val byUser = LinkedHashMap<String, ChatResponse>()

    @Synchronized
    fun get(userUuid: String): ChatResponse? = byUser[userUuid]

    @Synchronized
    fun put(userUuid: String, chat: ChatResponse) {
        if (userUuid.isBlank()) return
        byUser.remove(userUuid)
        byUser[userUuid] = chat
        while (byUser.size > MAX_ENTRIES) {
            val oldest = byUser.keys.firstOrNull() ?: break
            byUser.remove(oldest)
        }
    }

    @Synchronized
    fun clear() {
        byUser.clear()
    }
}
