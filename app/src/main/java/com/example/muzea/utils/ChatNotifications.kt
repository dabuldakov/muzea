package com.example.muzea.utils

import android.app.NotificationManager
import android.content.Context

/**
 * Идентификаторы и снятие push-уведомлений чата.
 *
 * Одна нотификация на чат: новый пуш заменяет старый (tag/id по uuid чата).
 * Когда пользователь открывает чат любым способом — из списка, по ссылке,
 * после возврата из другого экрана — уведомление этого чата снимаем.
 */
object ChatNotifications {

    /** Tag уведомления чата — тот же, что использует FCM-сервис. */
    fun tag(chatUuid: String): String = "chat_$chatUuid"

    /** id уведомления чата: один активный пуш на чат. */
    fun id(chatUuid: String): Int = chatUuid.hashCode()

    /** Убирает уведомление конкретного чата из шторки. */
    fun cancel(context: Context, chatUuid: String) {
        if (chatUuid.isBlank()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        manager.cancel(tag(chatUuid), id(chatUuid))
    }
}
