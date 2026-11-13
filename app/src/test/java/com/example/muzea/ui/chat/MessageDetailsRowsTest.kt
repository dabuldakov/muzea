package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageReaction
import com.example.muzea.domain.model.ReactionUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDetailsRowsTest {

    private fun message(reactions: List<MessageReaction>) = Message(
        messageUuid = "m1",
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "author",
        senderName = "Morris",
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null,
        reactions = reactions
    )

    private fun receipt(uuid: String, name: String) = ChatReceipt(
        userUuid = uuid,
        username = name,
        fullName = name,
        avatarUrl = "/a/$uuid.png",
        joinedAt = null,
        lastDeliveredMessageId = null,
        lastDeliveredAt = null,
        lastReadMessageId = null,
        lastReadAt = null
    )

    @Test
    fun `reaction authors map to rows and keep first-time order`() {
        val rows = MessageDetailsRows.reactions(
            message = message(
                listOf(
                    MessageReaction(
                        "👍", 2, false,
                        listOf(ReactionUser("u1", "2026-01-01T10:01"), ReactionUser("u2", "2026-01-01T10:02"))
                    )
                )
            ),
            receiptsByUuid = mapOf("u1" to receipt("u1", "Anna")),
            myUserUuid = "me",
            youLabel = "Вы",
            unknownLabel = "Участник",
            formatTime = { it.orEmpty().substringAfter('T').take(5) }
        )

        assertEquals(listOf("u1", "u2"), rows.map { it.userUuid })
        assertEquals("Anna", rows[0].name)
        assertEquals("10:01", rows[0].at)
        assertEquals("Участник", rows[1].name)
    }

    @Test
    fun `own reaction is labelled you`() {
        val rows = MessageDetailsRows.reactions(
            message = message(
                listOf(MessageReaction("🔥", 1, true, listOf(ReactionUser("me", null))))
            ),
            receiptsByUuid = emptyMap(),
            myUserUuid = "me",
            youLabel = "Вы",
            unknownLabel = "Участник",
            formatTime = { "" }
        )

        assertEquals("Вы", rows.single().name)
        assertNull(rows.single().at)
    }

    @Test
    fun `duplicate authors are kept once`() {
        val rows = MessageDetailsRows.reactions(
            message = message(
                listOf(
                    MessageReaction("👍", 1, false, listOf(ReactionUser("u1", null))),
                    MessageReaction("❤️", 1, false, listOf(ReactionUser("u1", null)))
                )
            ),
            receiptsByUuid = mapOf("u1" to receipt("u1", "Anna")),
            myUserUuid = null,
            youLabel = "Вы",
            unknownLabel = "Участник",
            formatTime = { "" }
        )

        assertEquals(1, rows.size)
    }
}
