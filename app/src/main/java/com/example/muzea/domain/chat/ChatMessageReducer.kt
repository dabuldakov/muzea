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

    // localUuid -> serverUuid: чтобы действия (правка/удаление/закреп/пересылка/
    // ответ) уходили на бэкенд с реальным ID, а не с локальным.
    private val localToServer = HashMap<String, String>()

    /** Реальный серверный uuid для локального (или тот же, если уже серверный). */
    fun serverUuid(localOrServer: String): String = localToServer[localOrServer] ?: localOrServer

    /** Локальный uuid для серверного (если это наше отправленное сообщение). */
    fun localUuidFor(serverUuid: String): String? = serverToLocal[serverUuid]

    /**
     * Версия списка с серверными идентификаторами — для кэша и превью списка
     * чатов, чтобы локальные uuid не «всплывали» после перезахода.
     */
    fun toServerIdentities(messages: List<Message>): List<Message> = messages.map { message ->
        message.copy(
            messageUuid = serverUuid(message.messageUuid),
            replyToMessageUuid = message.replyToMessageUuid?.let { serverUuid(it) }
        )
    }

    /**
     * Стартовый список из кэша: сервер отдаёт «сначала новые», показываем по
     * возрастанию. Удалённые сообщения не показываем вовсе — вместо заглушки
     * «сообщение удалено» они просто исчезают из переписки.
     */
    fun seed(cached: List<Message>): List<Message> =
        cached.filterNot { it.isDeleted }.sortedWith(MessageComparator())

    /**
     * Сливает серверную пачку с текущим списком. Существующие элементы имеют
     * приоритет (перекрывают входящие), а серверное эхо уже отправленных нами
     * сообщений пропускается — оно показано локальным пузырём.
     */
    fun merge(current: List<Message>, incoming: List<Message>): List<Message> {
        val merged = LinkedHashMap<String, Message>()
        // Если сообщение удалили (в том числе с другого устройства), сервер
        // присылает его с isDeleted — убираем и локальную копию.
        val deleted = incoming.filter { it.isDeleted }
            .mapTo(HashSet()) { it.messageUuid }
        for (message in incoming) {
            if (message.isDeleted) continue
            if (serverToLocal.containsKey(message.messageUuid)) continue
            merged[message.messageUuid] = message
        }
        for (message in current) {
            if (message.isDeleted || message.messageUuid in deleted) continue
            // Серверные данные важнее локальной копии: так приходят снятые
            // реакции, повреждения и правки старых сообщений. Локальные
            // пузыри, которых нет во входящей пачке, остаются.
            merged.putIfAbsent(message.messageUuid, message)
        }
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
        localToServer[localUuid] = serverMessage.messageUuid
        return current
            .map { existing ->
                if (existing.messageUuid == localUuid) {
                    // Цитата ответа тоже может ссылаться на наше сообщение —
                    // переводим серверный uuid в локальный для отображения.
                    val replyTo = serverMessage.replyToMessageUuid
                        ?.let { serverToLocal[it] ?: it }
                        ?: existing.replyToMessageUuid
                    serverMessage.copy(
                        messageUuid = localUuid,
                        replyToMessageUuid = replyTo,
                        // Сервер пока не знает про power — не теряем заряд,
                        // накопленный при удержании кнопки.
                        power = if (serverMessage.power > 0) serverMessage.power else existing.power,
                        reactions = if (serverMessage.reactions.isNotEmpty()) {
                            serverMessage.reactions
                        } else {
                            existing.reactions
                        }
                    )
                } else {
                    existing
                }
            }
            .sortedWith(MessageComparator())
    }

    /**
     * Заменяет сообщение с тем же uuid (например, после редактирования),
     * не меняя порядок списка.
     */
    fun updateMessage(current: List<Message>, updated: Message): List<Message> =
        current.map { existing ->
            if (existing.messageUuid == updated.messageUuid) updated else existing
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
