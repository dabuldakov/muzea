package com.example.muzea.data.repository

import android.app.Application
import android.util.Base64
import com.example.muzea.utils.TokenManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TokenChatUserIdentityTest {

    private val tokenManager = mockk<TokenManager>()
    private val identity = TokenChatUserIdentity(tokenManager)

    private fun jwt(payload: String): String {
        val encoded = Base64.encodeToString(
            payload.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "header.$encoded.signature"
    }

    @Test
    fun `no token yields null identity`() {
        every { tokenManager.getChatToken() } returns null

        assertNull(identity.userUuid)
    }

    @Test
    fun `extracts sub claim from jwt payload`() {
        every { tokenManager.getChatToken() } returns jwt("""{"sub":"user-1","exp":1}""")

        assertEquals("user-1", identity.userUuid)
    }

    @Test
    fun `malformed token yields null identity`() {
        every { tokenManager.getChatToken() } returns "not-a-jwt"

        assertNull(identity.userUuid)
    }

    @Test
    fun `payload without sub yields null identity`() {
        every { tokenManager.getChatToken() } returns jwt("""{"exp":1}""")

        assertNull(identity.userUuid)
    }
}
