package com.example.muzea.domain.chat

import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageReducerTest {

    private fun message(uuid: String, createdAt: String?) = Message(
        messageUuid = uuid,
        chatUuid = "chat-1",
        senderId = null,
        senderUuid = "me",
        senderName = null,
        senderAvatar = null,
        text = uuid,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null
    )

    @Test
    fun `seed sorts oldest first and puts undated last`() {
        val result = ChatMessageReducer().seed(
            listOf(
                message("m2", "2026-01-02T00:00:00"),
                message("local", null),
                message("m1", "2026-01-01T00:00:00")
            )
        )

        assertEquals(listOf("m1", "m2", "local"), result.map { it.messageUuid })
    }

    @Test
    fun `merge keeps existing messages and appends new ones in order`() {
        val reducer = ChatMessageReducer()
        val current = listOf(message("m1", "2026-01-01T00:00:00"))

        val result = reducer.merge(
            current,
            listOf(
                message("m1", "2026-01-01T00:00:00"),
                message("m2", "2026-01-02T00:00:00")
            )
        )

        assertEquals(listOf("m1", "m2"), result.map { it.messageUuid })
    }

    @Test
    fun `server echo updates local bubble keeping its uuid`() {
        val reducer = ChatMessageReducer()
        val current = listOf(message("local-1", null))
        val server = message("server-1", "2026-01-01T00:00:00")

        val result = reducer.applyServerEcho(current, "local-1", server)

        assertEquals(1, result.size)
        assertEquals("local-1", result.single().messageUuid)
        assertEquals("2026-01-01T00:00:00", result.single().createdAt)
    }

    @Test
    fun `merged server echo is not duplicated`() {
        val reducer = ChatMessageReducer()
        val server = message("server-1", "2026-01-01T00:00:00")
        val echoed = reducer.applyServerEcho(listOf(message("local-1", null)), "local-1", server)

        val result = reducer.merge(echoed, listOf(server))

        assertEquals(1, result.size)
        assertEquals("local-1", result.single().messageUuid)
    }
}
