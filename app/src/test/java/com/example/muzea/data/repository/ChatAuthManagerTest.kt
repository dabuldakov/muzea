package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.R
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.FcmTokenRequest
import com.example.muzea.utils.TokenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatAuthManagerTest {

    private val api = mockk<ChatApiService>()
    private val tokenManager = mockk<TokenManager>()
    private val context = mockk<Context>(relaxed = true)

    private fun manager() = ChatAuthManager(api, tokenManager, context)

    private fun profile(userUuid: String = "uuid-1") = Response.success(
        ChatUserResponse(
            userUuid = userUuid,
            username = "alice",
            email = "alice@example.com",
            firstName = null,
            lastName = null,
            fullName = "Alice",
            avatarUrl = null,
            isOnline = false
        )
    )

    @Test
    fun `returns false when there is no access token`() = runTest {
        every { tokenManager.getToken() } returns null
        every { context.getString(R.string.error_not_logged_in) } returns "not logged in"

        val manager = manager()
        val result = manager.isAuthenticated()

        assertFalse(result)
        assertNotNull(manager.lastFailureMessage)
    }

    @Test
    fun `caches chat user uuid from profile on first use`() = runTest {
        every { tokenManager.getToken() } returns "access"
        every { tokenManager.getChatUserUuid() } returns null
        every { tokenManager.getFcmToken() } returns null
        coEvery { api.getMyProfile() } returns profile("chat-uuid-9")

        val result = manager().isAuthenticated()

        assertTrue(result)
        verify { tokenManager.saveChatUserUuid("chat-uuid-9") }
    }

    @Test
    fun `does not refetch profile when uuid already cached`() = runTest {
        every { tokenManager.getToken() } returns "access"
        every { tokenManager.getChatUserUuid() } returns "cached-uuid"
        every { tokenManager.getFcmToken() } returns null

        assertTrue(manager().isAuthenticated())

        coVerify(exactly = 0) { api.getMyProfile() }
    }

    @Test
    fun `registers fcm token when present and changed`() = runTest {
        every { tokenManager.getToken() } returns "access"
        every { tokenManager.getChatUserUuid() } returns "cached-uuid"
        every { tokenManager.getFcmToken() } returns "fcm-1"
        every { tokenManager.getRegisteredFcmToken() } returns null
        every { tokenManager.getDeviceId() } returns "device-1"
        coEvery { api.registerFcmToken(any()) } returns Response.success(Unit)

        assertTrue(manager().isAuthenticated())

        coVerify { api.registerFcmToken(any<FcmTokenRequest>()) }
        verify { tokenManager.saveRegisteredFcmToken("fcm-1") }
    }
}
