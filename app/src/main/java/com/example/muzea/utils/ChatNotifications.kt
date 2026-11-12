package com.example.muzea.utils

import android.app.NotificationManager
import android.content.Context

/**
 * Одно уведомление на чат: тег и id зависят только от UUID чата.
 *
 * Благодаря этому приложение может снять уведомление, как только
 * пользователь прочитал чат — даже если он открыл его не из пуша.
 */
object ChatNotifications {

    fun tag(chatUuid: String): String = "chat_$chatUuid"

    fun id(chatUuid: String): Int = chatUuid.hashCode()

    fun cancel(context: Context, chatUuid: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(tag(chatUuid), id(chatUuid)) }
    }
}
