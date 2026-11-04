package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.R
import com.example.muzea.domain.model.Message

/**
 * Имя автора цитируемого сообщения. Оригинал может быть не загружен — тогда
 * показываем нейтральную подпись.
 */
fun replySenderName(context: Context, message: Message?, myUserUuid: String?): String {
    if (message == null) return context.getString(R.string.reply_unknown_sender)
    if (message.senderUuid != null && message.senderUuid == myUserUuid) {
        return context.getString(R.string.reply_you)
    }
    return message.senderName?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.reply_unknown_sender)
}

/** Короткий текст цитаты: текст, тип вложения или заглушка. */
fun replyPreviewText(context: Context, message: Message?): String {
    if (message == null) return context.getString(R.string.reply_quote_placeholder)
    if (message.isDeleted) return context.getString(R.string.reply_quote_deleted)
    val text = message.text?.trim()
    if (!text.isNullOrEmpty()) return text
    if (isGifMessage(message)) return context.getString(R.string.reply_quote_gif)
    if (message.attachments.isNotEmpty()) {
        return context.getString(R.string.reply_quote_attachment)
    }
    return context.getString(R.string.reply_quote_placeholder)
}

private fun isGifMessage(message: Message): Boolean =
    message.messageType.equals("GIF", ignoreCase = true) ||
        message.attachments.any { it.type.equals("GIF", ignoreCase = true) }
