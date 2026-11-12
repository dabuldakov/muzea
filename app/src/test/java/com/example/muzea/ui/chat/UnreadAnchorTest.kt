package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadAnchorTest {

    private fun message(uuid: String, senderUuid: String) = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = senderUuid,
        senderName = null,
        senderAvatar = null,
        text = uuid,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    @Test
    fun `first unread skips own messages at the tail`() {
        val messages = listOf(
            message("a", "other"),
            message("b", "me"),
            message("c", "other"),
            message("d", "me"),
            message("e", "other")
        )
        // Непрочитанных трое: a, c, e.
        assertEquals(0, UnreadAnchor.firstUnreadIndex(messages, 3, "me"))
        assertEquals(2, UnreadAnchor.firstUnreadIndex(messages, 2, "me"))
    }

    @Test
    fun `own messages are not counted as unread`() {
        val messages = listOf(
            message("a", "other"),
            message("b", "me"),
            message("c", "me")
        )
        assertEquals(0, UnreadAnchor.firstUnreadIndex(messages, 1, "me"))
    }

    @Test
    fun `no unread means no anchor`() {
        val messages = listOf(message("a", "other"))
        assertEquals(-1, UnreadAnchor.firstUnreadIndex(messages, 0, "me"))
        assertEquals(-1, UnreadAnchor.firstUnreadIndex(emptyList(), 5, "me"))
    }

    @Test
    fun `counter larger than the loaded page starts from the top`() {
        val messages = listOf(message("a", "other"), message("b", "other"))
        assertEquals(0, UnreadAnchor.firstUnreadIndex(messages, 50, "me"))
    }
}
