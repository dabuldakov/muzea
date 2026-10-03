package com.example.muzea.domain.chat

import com.example.muzea.domain.model.Message

/**
 * Чистая логика формирования списка сообщений переписки: сортировка по времени,
 * слияние с серверным списком и подмена локального пузыря серверным эхом без
 * смены идентификатора (иначе DiffUtil удаляет и вставляет строку — список мигает).
 *
 * Вынесено из ChatConversationViewModel, чтобы покрывать сценарии без Android.
 */
class ChatMessageReducer {

    // serverUuid -> localUuid для наших отправленных сообщений.
    private val serverToLocal = HashMap<String, String>()

    /** Стартовый список из кэша: сервер отдаёт «сначала новые», показываем по возрастанию. */
    fun seed(cached: List<Message>): List<Message> = cached.sortedWith(MessageComparator())

    /**
     * Сливает серверную пачку с текущим списком. Существующие элементы имеют
     * приоритет (перекрывают входящие), а серверное эхо уже отправленных нами
     * сообщений пропускается — оно показано локальным пузырём.
     */
    fun merge(current: List<Message>, incoming: List<Message>): List<Message> {
        val merged = LinkedHashMap<String, Message>()
        for (message in incoming) {
            if (serverToLocal.containsKey(message.messageUuid)) continue
            merged[message.messageUuid] = message
        }
        for (message in current) merged[message.messageUuid] = message
        return merged.values.sortedWith(MessageComparator())
    }

    /**
     * Обновляет локальный пузырь данными серверного эха, сохраняя его uuid,
     * и запоминает соответствие серверного uuid локальному.
     */
    fun applyServerEcho(
        current: List<Message>,
        localUuid: String,
        serverMessage: Message
    ): List<Message> {
        serverToLocal[serverMessage.messageUuid] = localUuid
        return current
            .map { existing ->
                if (existing.messageUuid == localUuid) {
                    serverMessage.copy(messageUuid = localUuid)
                } else {
                    existing
                }
            }
            .sortedWith(MessageComparator())
    }

    private class MessageComparator : Comparator<Message> {
        override fun compare(a: Message, b: Message): Int {
            val ta = a.createdAt ?: ""
            val tb = b.createdAt ?: ""
            if (ta.isEmpty() && tb.isEmpty()) return 0
            if (ta.isEmpty()) return 1
            if (tb.isEmpty()) return -1
            return ta.compareTo(tb)
        }
    }
}
