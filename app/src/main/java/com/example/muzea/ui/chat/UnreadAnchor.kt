package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message

/**
 * Где начинается блок непрочитанных.
 *
 * Сервер считает непрочитанными только чужие сообщения, поэтому просто
 * «размер минус счётчик» даёт неверную позицию: в хвосте ленты есть и свои.
 * Идём с конца и отсчитываем чужие, пока не кончится счётчик.
 */
object UnreadAnchor {

    /** Индекс первого непрочитанного или -1, если считать нечего. */
    fun firstUnreadIndex(
        messages: List<Message>,
        unreadCount: Long,
        myUserUuid: String?
    ): Int {
        if (messages.isEmpty() || unreadCount <= 0) return -1
        var remaining = unreadCount
        for (index in messages.indices.reversed()) {
            val sender = messages[index].senderUuid
            // Свои сообщения непрочитанными не считаются.
            if (myUserUuid != null && sender == myUserUuid) continue
            remaining--
            if (remaining <= 0) return index
        }
        return 0
    }
}
