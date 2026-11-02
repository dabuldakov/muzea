package com.example.muzea.data.repository

import com.example.muzea.data.api.AuthTokenApi
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.AuthServiceTokenResponse
import com.example.muzea.utils.TokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

class AuthTokenRefresherTest {

    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val api = mockk<AuthTokenApi>()
    private val refresher = AuthTokenRefresher(tokenManager, api)

    @Test
    fun `returns null when no refresh token is stored`() {
        every { tokenManager.getAuthRefreshToken() } returns null

        assertNull(refresher.refreshToken())
        verify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `blank refresh token is treated as missing`() {
        every { tokenManager.getAuthRefreshToken() } returns "  "

        assertNull(refresher.refreshToken())
    }

    @Test
    fun `successful refresh stores both tokens`() {
        every { tokenManager.getAuthRefreshToken() } returns "old-refresh"
        every { tokenManager.getDeviceId() } returns "device-1"
        val call = mockk<Call<AuthServiceTokenResponse>>()
        every { api.refresh(any()) } returns call
        every { call.execute() } returns httpOk(tokens("new-access", "new-refresh"))

        val token = refresher.refreshToken()

        assertEquals("new-access", token)
        verify(exactly = 1) { tokenManager.saveToken("new-access") }
        verify(exactly = 1) { tokenManager.saveAuthRefreshToken("new-refresh") }
    }

    @Test
    fun `rejected refresh keeps the old tokens`() {
        every { tokenManager.getAuthRefreshToken() } returns "old-refresh"
        val call = mockk<Call<AuthServiceTokenResponse>>()
        every { api.refresh(any()) } returns call
        every { call.execute() } returns Response.error(401, "{}".toResponseBody())

        assertNull(refresher.refreshToken())
        verify(exactly = 0) { tokenManager.saveToken(any()) }
    }

    @Test
    fun `empty body is treated as failure`() {
        every { tokenManager.getAuthRefreshToken() } returns "old-refresh"
        val call = mockk<Call<AuthServiceTokenResponse>>()
        every { api.refresh(any()) } returns call
        every { call.execute() } returns Response.success<AuthServiceTokenResponse>(null)

        assertNull(refresher.refreshToken())
    }

    @Test
    fun `network failure returns null`() {
        every { tokenManager.getAuthRefreshToken() } returns "old-refresh"
        val call = mockk<Call<AuthServiceTokenResponse>>()
        every { api.refresh(any()) } returns call
        every { call.execute() } throws IOException("offline")

        assertNull(refresher.refreshToken())
    }

    @Test
    fun `current token comes from the token manager`() {
        every { tokenManager.getToken() } returns "access"

        assertEquals("access", refresher.currentToken())
    }

    private fun tokens(access: String, refresh: String) = AuthServiceTokenResponse(
        accessToken = access,
        refreshToken = refresh,
        tokenType = "Bearer",
        expiresIn = 900,
        user = null
    )

}
