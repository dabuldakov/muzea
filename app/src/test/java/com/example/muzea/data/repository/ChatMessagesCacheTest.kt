package com.example.muzea.data.repository

import com.example.muzea.data.model.MessageResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatMessagesCacheTest {

    private fun message(uuid: String) = MessageResponse(
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
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    @Before
    fun setUp() = ChatMessagesCache.clear()

    @After
    fun tearDown() = ChatMessagesCache.clear()

    @Test
    fun `unknown chat returns empty list`() {
        assertFalse(ChatMessagesCache.has("chat-1"))
        assertTrue(ChatMessagesCache.get("chat-1").isEmpty())
    }

    @Test
    fun `put keeps order and marks chat as cached`() {
        ChatMessagesCache.put("chat-1", listOf(message("m2"), message("m1")))

        assertTrue(ChatMessagesCache.has("chat-1"))
        assertEquals(listOf("m2", "m1"), ChatMessagesCache.get("chat-1").map { it.messageUuid })
    }

    @Test
    fun `put overwrites previous contents`() {
        ChatMessagesCache.put("chat-1", listOf(message("m1")))
        ChatMessagesCache.put("chat-1", listOf(message("m9")))

        assertEquals(listOf("m9"), ChatMessagesCache.get("chat-1").map { it.messageUuid })
    }

    @Test
    fun `chats are isolated from each other`() {
        ChatMessagesCache.put("chat-1", listOf(message("a")))
        ChatMessagesCache.put("chat-2", listOf(message("b")))

        assertEquals(listOf("a"), ChatMessagesCache.get("chat-1").map { it.messageUuid })
        assertEquals(listOf("b"), ChatMessagesCache.get("chat-2").map { it.messageUuid })
    }

    @Test
    fun `cache is capped and evicts the oldest chats`() {
        (1..60).forEach { ChatMessagesCache.put("chat-$it", listOf(message("m-$it"))) }

        assertFalse(ChatMessagesCache.has("chat-1"))
        assertFalse(ChatMessagesCache.has("chat-10"))
        assertTrue(ChatMessagesCache.has("chat-11"))
        assertTrue(ChatMessagesCache.has("chat-60"))
    }

    @Test
    fun `clear removes all chats`() {
        ChatMessagesCache.put("chat-1", listOf(message("m1")))

        ChatMessagesCache.clear()

        assertFalse(ChatMessagesCache.has("chat-1"))
    }
}
