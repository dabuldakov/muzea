package com.example.muzea.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveChatTrackerTest {

    @After
    fun tearDown() {
        ActiveChatTracker.chatUuid = null
    }

    @Test
    fun `defaults to no active chat`() {
        ActiveChatTracker.chatUuid = null

        assertNull(ActiveChatTracker.chatUuid)
    }

    @Test
    fun `stores and clears the active chat uuid`() {
        ActiveChatTracker.chatUuid = "chat-1"
        assertEquals("chat-1", ActiveChatTracker.chatUuid)

        ActiveChatTracker.chatUuid = null
        assertNull(ActiveChatTracker.chatUuid)
    }
}
