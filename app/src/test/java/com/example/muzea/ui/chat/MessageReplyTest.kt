package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.R
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Цитата ответа: подпись автора и короткий текст оригинала. Контекст мокается,
 * поэтому проверяем, к какому строковому ресурсу обращается логика, а не
 * локализованный текст.
 */
class MessageReplyTest {

    private val strings = mapOf(
        R.string.reply_you to "YOU",
        R.string.reply_unknown_sender to "UNKNOWN",
        R.string.reply_quote_placeholder to "PLACEHOLDER",
        R.string.reply_quote_deleted to "DELETED",
        R.string.reply_quote_gif to "GIF",
        R.string.reply_quote_attachment to "ATTACHMENT"
    )

    private val context: Context = mockk {
        every { getString(any()) } answers { strings[firstArg<Int>()] ?: "?" }
    }

    private fun message(
        uuid: String = "m1",
        senderUuid: String? = "u-1",
        senderName: String? = "Anna",
        text: String? = null,
        messageType: String? = "TEXT",
        isDeleted: Boolean = false,
        attachments: List<MessageAttachment> = emptyList()
    ) = Message(
        messageUuid = uuid,
        chatUuid = "chat-1",
        senderId = 1L,
        senderUuid = senderUuid,
        senderName = senderName,
        senderAvatar = null,
        text = text,
        messageType = messageType,
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = isDeleted,
        isPinned = false,
        createdAt = null,
        updatedAt = null,
        attachments = attachments
    )

    private fun attachment(type: String) = MessageAttachment(
        fileUrl = "/file",
        thumbnailUrl = null,
        type = type,
        width = null,
        height = null
    )

    @Test
    fun `sender name falls back to unknown when original is missing`() {
        assertEquals("UNKNOWN", replySenderName(context, null, "me"))
    }

    @Test
    fun `sender name is You for my own message`() {
        assertEquals("YOU", replySenderName(context, message(senderUuid = "me"), "me"))
    }

    @Test
    fun `sender name falls back to unknown when it is blank`() {
        assertEquals(
            "UNKNOWN",
            replySenderName(context, message(senderName = "  "), "other")
        )
        assertEquals(
            "UNKNOWN",
            replySenderName(context, message(senderName = null), "other")
        )
    }

    @Test
    fun `sender name uses the author for other people`() {
        assertEquals("Anna", replySenderName(context, message(senderName = "Anna"), "other"))
    }

    @Test
    fun `preview falls back to placeholder when original is missing`() {
        assertEquals("PLACEHOLDER", replyPreviewText(context, null))
    }

    @Test
    fun `preview says deleted for a deleted original`() {
        assertEquals("DELETED", replyPreviewText(context, message(isDeleted = true)))
    }

    @Test
    fun `preview returns trimmed text when present`() {
        assertEquals("hello", replyPreviewText(context, message(text = "  hello  ")))
    }

    @Test
    fun `preview says GIF for gif message type`() {
        assertEquals(
            "GIF",
            replyPreviewText(context, message(messageType = "gif"))
        )
    }

    @Test
    fun `preview says GIF when a gif attachment exists`() {
        assertEquals(
            "GIF",
            replyPreviewText(context, message(attachments = listOf(attachment("GIF"))))
        )
    }

    @Test
    fun `preview says attachment for non-gif attachments`() {
        assertEquals(
            "ATTACHMENT",
            replyPreviewText(context, message(attachments = listOf(attachment("IMAGE"))))
        )
    }

    @Test
    fun `preview falls back to placeholder for an empty message`() {
        assertEquals(
            "PLACEHOLDER",
            replyPreviewText(context, message(text = "   ", messageType = "TEXT"))
        )
    }
}
