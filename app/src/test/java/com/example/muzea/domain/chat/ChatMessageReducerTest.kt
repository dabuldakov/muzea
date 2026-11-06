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
    fun `deleted messages are dropped from seed and merge`() {
        val reducer = ChatMessageReducer()
        val deleted = message("gone", "2026-01-01T00:00:00").copy(isDeleted = true)

        val seeded = reducer.seed(listOf(deleted, message("m1", "2026-01-02T00:00:00")))
        val merged = reducer.merge(
            listOf(message("m1", "2026-01-02T00:00:00")),
            listOf(deleted, message("m2", "2026-01-03T00:00:00"))
        )

        assertEquals(listOf("m1"), seeded.map { it.messageUuid })
        assertEquals(listOf("m1", "m2"), merged.map { it.messageUuid })
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
    fun `server delete marker removes an existing message`() {
        val reducer = ChatMessageReducer()
        val existing = message("m1", "2026-01-02T00:00:00")

        val result = reducer.merge(
            listOf(existing),
            listOf(existing.copy(isDeleted = true))
        )

        assertEquals(emptyList<String>(), result.map { it.messageUuid })
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
    fun `server echo keeps the local reply reference when the server omits it`() {
        val reducer = ChatMessageReducer()
        val current = listOf(message("local-1", null).copy(replyToMessageUuid = "orig"))
        val server = message("server-1", "2026-01-01T00:00:00")

        val result = reducer.applyServerEcho(current, "local-1", server)

        assertEquals("orig", result.single().replyToMessageUuid)
    }

    @Test
    fun `server echo keeps its own reply reference when present`() {
        val reducer = ChatMessageReducer()
        val current = listOf(message("local-1", null).copy(replyToMessageUuid = "local-orig"))
        val server = message("server-1", "2026-01-01T00:00:00")
            .copy(replyToMessageUuid = "server-orig")

        val result = reducer.applyServerEcho(current, "local-1", server)

        assertEquals("server-orig", result.single().replyToMessageUuid)
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

    @Test
    fun `updateMessage replaces the matching message keeping order`() {
        val reducer = ChatMessageReducer()
        val current = listOf(
            message("m1", "2026-01-01T00:00:00"),
            message("m2", "2026-01-02T00:00:00"),
            message("m3", "2026-01-03T00:00:00")
        )

        val result = reducer.updateMessage(current, current[1].copy(text = "edited"))

        assertEquals(listOf("m1", "m2", "m3"), result.map { it.messageUuid })
        assertEquals("edited", result[1].text)
        assertEquals("m1", result[0].text)
    }

    @Test
    fun `updateMessage leaves unrelated messages untouched`() {
        val reducer = ChatMessageReducer()
        val current = listOf(message("m1", "2026-01-01T00:00:00"))

        val result = reducer.updateMessage(current, message("other", "2026-01-02T00:00:00"))

        assertEquals(listOf("m1"), result.map { it.messageUuid })
    }

    @Test
    fun `seed keeps undated messages after dated ones`() {
        val reducer = ChatMessageReducer()
        val result = reducer.seed(
            listOf(message("undated", null), message("dated", "2026-01-01T00:00:00"))
        )

        assertEquals(listOf("dated", "undated"), result.map { it.messageUuid })
    }
}
