package com.example.muzea.ui.chat

import android.content.Context

/**
 * Черновики сообщений по чатам.
 *
 * Простое SharedPreferences-хранилище: текст переживает выход из чата и
 * перезапуск приложения, но не отправляется автоматически.
 */
object ChatDrafts {

    private const val PREFS = "chat_drafts"

    fun get(context: Context, chatUuid: String): String? {
        if (chatUuid.isBlank()) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(chatUuid, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun save(context: Context, chatUuid: String, text: String?) {
        if (chatUuid.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (text.isNullOrBlank()) {
            prefs.edit().remove(chatUuid).apply()
        } else {
            prefs.edit().putString(chatUuid, text).apply()
        }
    }

    fun clear(context: Context, chatUuid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(chatUuid)
            .apply()
    }

    /** Удаляет все черновики: после выхода или удаления аккаунта чужих текстов быть не должно. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
