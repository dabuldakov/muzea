package com.example.muzea.data.repository

import com.example.muzea.domain.model.Chat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PrivateChatCacheTest {

    private fun chat(uuid: String) = Chat(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = null,
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    @Before
    fun setUp() = PrivateChatCache.clear()

    @After
    fun tearDown() = PrivateChatCache.clear()

    @Test
    fun `empty cache returns null`() {
        assertNull(PrivateChatCache.get("user-1"))
    }

    @Test
    fun `put then get returns the chat`() {
        PrivateChatCache.put("user-1", chat("chat-1"))

        assertEquals("chat-1", PrivateChatCache.get("user-1")?.chatUuid)
    }

    @Test
    fun `put ignores blank uuid`() {
        PrivateChatCache.put("", chat("chat-1"))
        PrivateChatCache.put("   ", chat("chat-2"))

        assertNull(PrivateChatCache.get(""))
        assertNull(PrivateChatCache.get("   "))
    }

    @Test
    fun `put overwrites previous mapping`() {
        PrivateChatCache.put("user-1", chat("chat-1"))
        PrivateChatCache.put("user-1", chat("chat-2"))

        assertEquals("chat-2", PrivateChatCache.get("user-1")?.chatUuid)
    }

    @Test
    fun `cache is capped and evicts the oldest entries`() {
        (1..250).forEach { PrivateChatCache.put("user-$it", chat("chat-$it")) }

        assertNull(PrivateChatCache.get("user-1"))
        assertNull(PrivateChatCache.get("user-50"))
        assertEquals("chat-51", PrivateChatCache.get("user-51")?.chatUuid)
        assertEquals("chat-250", PrivateChatCache.get("user-250")?.chatUuid)
    }

    @Test
    fun `clear empties the cache`() {
        PrivateChatCache.put("user-1", chat("chat-1"))

        PrivateChatCache.clear()

        assertNull(PrivateChatCache.get("user-1"))
    }
}
