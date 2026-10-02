package com.example.muzea.data.repository

import com.example.muzea.data.model.ChatResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatListCacheTest {

    private fun chat(uuid: String) = ChatResponse(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = "Chat $uuid",
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    @Before
    fun setUp() = ChatListCache.clear()

    @After
    fun tearDown() = ChatListCache.clear()

    @Test
    fun `empty cache reports no chats`() {
        assertFalse(ChatListCache.has())
        assertTrue(ChatListCache.get().isEmpty())
    }

    @Test
    fun `put makes chats available`() {
        ChatListCache.put(listOf(chat("a"), chat("b")))

        assertTrue(ChatListCache.has())
        assertEquals(listOf("a", "b"), ChatListCache.get().map { it.chatUuid })
    }

    @Test
    fun `put keeps server order`() {
        ChatListCache.put(listOf(chat("c"), chat("a"), chat("b")))

        assertEquals(listOf("c", "a", "b"), ChatListCache.get().map { it.chatUuid })
    }

    @Test
    fun `put replaces previous contents`() {
        ChatListCache.put(listOf(chat("a"), chat("b")))
        ChatListCache.put(listOf(chat("c")))

        assertEquals(listOf("c"), ChatListCache.get().map { it.chatUuid })
    }

    @Test
    fun `put drops chats with blank uuid`() {
        ChatListCache.put(listOf(chat("a"), chat(""), chat("   "), chat("b")))

        assertEquals(listOf("a", "b"), ChatListCache.get().map { it.chatUuid })
    }

    @Test
    fun `duplicated uuid is stored once with latest fields`() {
        ChatListCache.put(listOf(chat("a").copy(unreadCount = 1L), chat("a").copy(unreadCount = 7L)))

        val stored = ChatListCache.get()
        assertEquals(1, stored.size)
        assertEquals(7L, stored.single().unreadCount)
    }

    @Test
    fun `put empty list clears cache`() {
        ChatListCache.put(listOf(chat("a")))
        ChatListCache.put(emptyList())

        assertFalse(ChatListCache.has())
    }

    @Test
    fun `cache is capped so memory cannot grow without bound`() {
        ChatListCache.put((1..250).map { chat("chat-$it") })

        val stored = ChatListCache.get()
        assertEquals(200, stored.size)
        // Вытесняются самые старые, порядок сервера в остатке сохраняется.
        assertEquals("chat-51", stored.first().chatUuid)
        assertEquals("chat-250", stored.last().chatUuid)
    }

    @Test
    fun `clear empties cache`() {
        ChatListCache.put(listOf(chat("a")))
        ChatListCache.clear()

        assertFalse(ChatListCache.has())
        assertTrue(ChatListCache.get().isEmpty())
    }
}