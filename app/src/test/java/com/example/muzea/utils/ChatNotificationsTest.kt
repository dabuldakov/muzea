package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatNotificationsTest {

    @Test
    fun `tag and id depend only on chat uuid`() {
        val uuid = "682c2dab-4814-49d9-91fc-ce58638a6c73"
        assertEquals("chat_$uuid", ChatNotifications.tag(uuid))
        assertEquals(uuid.hashCode(), ChatNotifications.id(uuid))
    }

    @Test
    fun `different chats have different notifications`() {
        assertNotEquals(
            ChatNotifications.id("chat-a"),
            ChatNotifications.id("chat-b")
        )
    }
}
