package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageActionsBuilderTest {

    private fun message(
        uuid: String = "m1",
        sender: String = "me",
        messageId: Long? = 10,
        text: String? = "привет",
        pinned: Boolean = false,
        deleted: Boolean = false,
        attachments: List<MessageAttachment> = emptyList()
    ) = Message(
        messageUuid = uuid,
        messageId = messageId,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = sender,
        senderName = null,
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = deleted,
        isPinned = pinned,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null,
        attachments = attachments
    )

    private fun ids(message: Message, mine: String? = "me", group: Boolean = true) =
        MessageActionsBuilder.build(message, mine, group)
            .actions.map { it.id }

    @Test
    fun `own server message has edit delete and forward`() {
        val ids = ids(message())
        assertEquals(
            listOf(
                MessageActionsBottomSheet.ACTION_REACT,
                MessageActionsBottomSheet.ACTION_COPY,
                MessageActionsBottomSheet.ACTION_REPLY,
                MessageActionsBottomSheet.ACTION_SELECT,
                MessageActionsBottomSheet.ACTION_EDIT,
                MessageActionsBottomSheet.ACTION_DELETE,
                MessageActionsBottomSheet.ACTION_FORWARD,
                MessageActionsBottomSheet.ACTION_PIN
            ),
            ids
        )
        assertTrue(MessageActionsBuilder.build(message(), "me", true).showStatus)
    }

    @Test
    fun `foreign message can be reported and blocked`() {
        val ids = ids(message(sender = "other", messageId = 10))
        assertTrue(MessageActionsBottomSheet.ACTION_REPORT in ids)
        assertTrue(MessageActionsBottomSheet.ACTION_BLOCK in ids)
        assertFalse(MessageActionsBottomSheet.ACTION_DELETE in ids)
        assertFalse(MessageActionsBottomSheet.ACTION_EDIT in ids)
        assertFalse(MessageActionsBuilder.build(message(sender = "other"), "me", true).showStatus)
    }

    @Test
    fun `failed local message can be retried`() {
        val ids = MessageActionsBuilder.build(
            message(uuid = "local-1", messageId = null),
            myUserUuid = "me",
            isGroupChat = true,
            deliveryStatus = MessageDeliveryStatus.FAILED
        ).actions.map { it.id }
        assertEquals(
            listOf(
                MessageActionsBottomSheet.ACTION_REACT,
                MessageActionsBottomSheet.ACTION_RETRY,
                MessageActionsBottomSheet.ACTION_COPY
            ),
            ids
        )
    }

    @Test
    fun `pinned message offers unpin`() {
        val ids = ids(message(pinned = true))
        assertTrue(MessageActionsBottomSheet.ACTION_UNPIN in ids)
        assertFalse(MessageActionsBottomSheet.ACTION_PIN in ids)
    }

    @Test
    fun `deleted message has no actions`() {
        val result = MessageActionsBuilder.build(message(deleted = true), "me", true)
        assertTrue(result.actions.isEmpty())
        assertFalse(result.showStatus)
    }

    @Test
    fun `media without text can still be edited by its author`() {
        val attachment = MessageAttachment(
            fileUrl = "https://cdn/1.jpg",
            thumbnailUrl = null,
            type = "IMAGE",
            width = 1,
            height = 1
        )
        val ids = ids(message(text = null, attachments = listOf(attachment)))
        assertTrue(MessageActionsBottomSheet.ACTION_EDIT in ids)
        assertFalse(MessageActionsBottomSheet.ACTION_COPY in ids)
    }

    @Test
    fun `status is hidden in private chats`() {
        val result = MessageActionsBuilder.build(message(), "me", isGroupChat = false)
        assertFalse(result.showStatus)
    }

}
