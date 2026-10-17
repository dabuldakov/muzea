package com.example.muzea.data.repository

import com.example.muzea.utils.TokenManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenChatUserIdentityTest {

    private val tokenManager = mockk<TokenManager>()
    private val identity = TokenChatUserIdentity(tokenManager)

    @Test
    fun `returns cached chat user uuid`() {
        every { tokenManager.getChatUserUuid() } returns "chat-uuid-1"

        assertEquals("chat-uuid-1", identity.userUuid)
    }

    @Test
    fun `null when uuid not cached yet`() {
        every { tokenManager.getChatUserUuid() } returns null

        assertNull(identity.userUuid)
    }
}
