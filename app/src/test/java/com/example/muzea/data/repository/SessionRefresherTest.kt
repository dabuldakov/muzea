package com.example.muzea.data.repository

import com.example.muzea.data.api.AuthApi
import com.example.muzea.data.model.AuthResponse
import com.example.muzea.data.model.LoginRequest
import com.example.muzea.utils.TokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class SessionRefresherTest {

    private class FakeAuthApi(var result: Response<AuthResponse>) : AuthApi {
        var lastRequest: LoginRequest? = null

        override fun login(request: LoginRequest): Response<AuthResponse> {
            lastRequest = request
            return result
        }
    }

    @Test
    fun `refreshToken logs in with stored credentials and saves new token`() {
        val tokenManager = mockk<TokenManager>(relaxed = true)
        every { tokenManager.getUsername() } returns "user"
        every { tokenManager.getPassword() } returns "pass"
        val api = FakeAuthApi(Response.success(AuthResponse("new-token", "user", "USER")))
        val refresher = SessionRefresher(tokenManager, api)

        val token = refresher.refreshToken()

        assertEquals("new-token", token)
        assertEquals(LoginRequest("user", "pass"), api.lastRequest)
        verify { tokenManager.saveToken("new-token") }
        verify { tokenManager.saveUsername("user") }
    }

    @Test
    fun `refreshToken returns null without stored credentials`() {
        val tokenManager = mockk<TokenManager>(relaxed = true)
        every { tokenManager.getUsername() } returns null
        every { tokenManager.getPassword() } returns null
        val api = FakeAuthApi(Response.success(AuthResponse("x", "u", "U")))
        val refresher = SessionRefresher(tokenManager, api)

        assertNull(refresher.refreshToken())
        assertNull(api.lastRequest)
    }

    @Test
    fun `refreshToken returns null when login fails`() {
        val tokenManager = mockk<TokenManager>(relaxed = true)
        every { tokenManager.getUsername() } returns "user"
        every { tokenManager.getPassword() } returns "pass"
        val api = FakeAuthApi(Response.error(401, "".toResponseBody()))
        val refresher = SessionRefresher(tokenManager, api)

        assertNull(refresher.refreshToken())
    }
}
