package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageStatusCalculatorTest {

    private val me = "user-me"
    private val other = "user-other"

    @Test
    fun `incoming messages have no status`() {
        val message = message(5, sender = other)

        assertNull(MessageStatusCalculator.statusOf(message, listOf(receipt(other)), me))
    }

    @Test
    fun `local message is sending until the server echo`() {
        val message = message(null, sender = me)

        assertEquals(
            MessageDeliveryStatus.SENDING,
            MessageStatusCalculator.statusOf(message, emptyList(), me, receiptsLoaded = false)
        )
    }

    @Test
    fun `failed local message is marked failed`() {
        val message = message(null, sender = me)

        assertEquals(
            MessageDeliveryStatus.FAILED,
            MessageStatusCalculator.statusOf(
                message, emptyList(), me, failedMessages = setOf(message.messageUuid)
            )
        )
    }

    @Test
    fun `sent is shown until receipts are loaded`() {
        val message = message(10, sender = me)

        assertEquals(
            MessageDeliveryStatus.SENT,
            MessageStatusCalculator.statusOf(message, emptyList(), me, receiptsLoaded = false)
        )
    }

    @Test
    fun `private chat shows delivered when the peer received it`() {
        val message = message(10, sender = me)
        val peer = receipt(other, delivered = 10, read = null)

        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            MessageStatusCalculator.statusOf(message, listOf(peer), me)
        )
    }

    @Test
    fun `private chat shows read when the peer read it`() {
        val message = message(10, sender = me)
        val peer = receipt(other, delivered = 10, read = 10)

        assertEquals(
            MessageDeliveryStatus.READ,
            MessageStatusCalculator.statusOf(message, listOf(peer), me)
        )
    }

    @Test
    fun `group is delivered only when everyone received it`() {
        val message = message(10, sender = me)
        val first = receipt("u1", delivered = 10, read = 10)
        val second = receipt("u2", delivered = 10, read = null)

        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            MessageStatusCalculator.statusOf(message, listOf(first, second), me)
        )
    }

    @Test
    fun `group stays sent while someone has not received it`() {
        val message = message(10, sender = me)
        val first = receipt("u1", delivered = 10, read = 10)
        val second = receipt("u2", delivered = 9, read = null)

        assertEquals(
            MessageDeliveryStatus.SENT,
            MessageStatusCalculator.statusOf(message, listOf(first, second), me)
        )
    }

    @Test
    fun `group is read only when everyone read it`() {
        val message = message(10, sender = me)
        val first = receipt("u1", delivered = 10, read = 10)
        val second = receipt("u2", delivered = 10, read = 10)

        assertEquals(
            MessageDeliveryStatus.READ,
            MessageStatusCalculator.statusOf(message, listOf(first, second), me)
        )
    }

    @Test
    fun `participant who joined later does not hold the status`() {
        val message = message(10, sender = me, createdAt = "2026-01-01T10:00:00")
        val early = receipt(other, delivered = 10, read = 10, joinedAt = "2026-01-01T09:00:00")
        val late = receipt("late", delivered = null, read = null, joinedAt = "2026-02-01T10:00:00")

        assertEquals(
            MessageDeliveryStatus.READ,
            MessageStatusCalculator.statusOf(message, listOf(early, late), me)
        )
    }

    @Test
    fun `chat with yourself is considered read`() {
        val message = message(10, sender = me)

        assertEquals(
            MessageDeliveryStatus.READ,
            MessageStatusCalculator.statusOf(message, listOf(receipt(me)), me)
        )
    }

    @Test
    fun `statuses map contains only own server and local messages`() {
        val mine = message(10, sender = me)
        val incoming = message(11, sender = other)
        val local = message(null, sender = me)

        val statuses = MessageStatusCalculator.statuses(
            listOf(mine, incoming, local),
            listOf(receipt(other, delivered = 10, read = null)),
            me
        )

        assertEquals(MessageDeliveryStatus.DELIVERED, statuses[mine.messageUuid])
        assertEquals(MessageDeliveryStatus.SENDING, statuses[local.messageUuid])
        assertNull(statuses[incoming.messageUuid])
    }

    @Test
    fun `participant details exclude me and late joiners and sort by status`() {
        val message = message(10, sender = me, createdAt = "2026-01-01T10:00:00")
        val read = receipt("read", delivered = 10, read = 10)
        val sent = receipt("sent", delivered = 9, read = null)
        val late = receipt("late", delivered = null, read = null, joinedAt = "2026-02-01T10:00:00")

        val rows = MessageStatusCalculator.participantStatuses(
            message, listOf(receipt(me), read, sent, late), me
        )

        assertEquals(listOf("read", "sent"), rows.map { it.receipt.userUuid })
        assertEquals(MessageDeliveryStatus.READ, rows[0].status)
        assertEquals(MessageDeliveryStatus.SENT, rows[1].status)
    }

    private fun message(
        messageId: Long?,
        sender: String,
        createdAt: String = "2026-01-01T10:00:00"
    ) = Message(
        messageUuid = "m-${messageId ?: "local"}",
        messageId = messageId,
        chatUuid = "chat-1",
        senderId = null,
        senderUuid = sender,
        senderName = null,
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null
    )

    private fun receipt(
        uuid: String,
        delivered: Long? = null,
        read: Long? = null,
        joinedAt: String = "2025-01-01T00:00:00"
    ) = ChatReceipt(
        userUuid = uuid,
        username = uuid,
        fullName = uuid,
        avatarUrl = null,
        joinedAt = joinedAt,
        lastDeliveredMessageId = delivered,
        lastDeliveredAt = null,
        lastReadMessageId = read,
        lastReadAt = null
    )
}
