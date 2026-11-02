package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.api.AuthServiceApi
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.AuthServiceMessage
import com.example.muzea.data.model.AuthServiceTokenResponse
import com.example.muzea.data.model.AuthServiceUser
import com.example.muzea.utils.TokenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AuthRepositoryTest {

    private val context = mockk<Context>()
    private val api = mockk<AuthServiceApi>()
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        every { context.getString(R.string.error_no_connection) } returns "no connection"
        every { context.getString(R.string.error_invalid_code) } returns "invalid code"
        every { context.getString(R.string.error_code_expired) } returns "expired"
        every { context.getString(R.string.error_too_many_attempts) } returns "too many"
        every { context.getString(R.string.error_email_registered) } returns "registered"
        every { context.getString(R.string.error_username_taken) } returns "taken"
        every { context.getString(R.string.error_wait_before_resend) } returns "wait"
        every { context.getString(R.string.error_generic, *anyVararg()) } returns "generic"
        every { tokenManager.getDeviceId() } returns "device-1"
        repository = AuthRepository(api, tokenManager, context)
    }

    @Test
    fun `register emits loading then success`() = runTest {
        coEvery { api.register(any()) } returns httpOk(AuthServiceMessage("sent"))

        val results = repository.register("a@b.c", "vovan", null).toList()

        assertTrue(results.first() is Resource.Loading)
        assertTrue(results.last() is Resource.Success)
    }

    @Test
    fun `register localizes an invalid code error`() = runTest {
        coEvery { api.register(any()) } returns httpError(400, """{"message":"Invalid code"}""")

        val last = repository.register("a@b.c", "vovan", null).toList().last()

        assertEquals("invalid code", (last as Resource.Error).message)
    }

    @Test
    fun `resend localizes an expired code error`() = runTest {
        coEvery { api.resendVerification(any()) } returns
            httpError(400, """{"message":"Code expired"}""")

        val last = repository.resendVerification("a@b.c").toList().last()

        assertEquals("expired", (last as Resource.Error).message)
    }

    @Test
    fun `request code localizes too many attempts`() = runTest {
        coEvery { api.requestLoginCode(any()) } returns
            httpError(429, """{"message":"Too many attempts"}""")

        val last = repository.requestLoginCode("a@b.c").toList().last()

        assertEquals("too many", (last as Resource.Error).message)
    }

    @Test
    fun `registration localizes a taken username`() = runTest {
        coEvery { api.register(any()) } returns httpError(409, """{"message":"Username already taken"}""")

        val last = repository.register("a@b.c", "vovan", null).toList().last()

        assertEquals("taken", (last as Resource.Error).message)
    }

    @Test
    fun `request without a body falls back to the generic message`() = runTest {
        coEvery { api.requestLoginCode(any()) } returns httpError(500, "")

        val last = repository.requestLoginCode("a@b.c").toList().last()

        assertEquals("generic", (last as Resource.Error).message)
    }

    @Test
    fun `network failure is reported as no connection`() = runTest {
        coEvery { api.requestLoginCode(any()) } throws IOException("offline")

        val last = repository.requestLoginCode("a@b.c").toList().last()

        assertEquals("no connection", (last as Resource.Error).message)
    }

    @Test
    fun `successful login code verification persists the token pair and user`() = runTest {
        coEvery { api.verifyLoginCode(any()) } returns httpOk(tokens())

        val last = repository.verifyLoginCode("a@b.c", "1234").toList().last()

        assertTrue(last is Resource.Success)
        verify(exactly = 1) { tokenManager.saveToken("access") }
        verify(exactly = 1) { tokenManager.saveAuthRefreshToken("refresh") }
        verify(exactly = 1) { tokenManager.saveUsername("vovan") }
        verify(exactly = 1) { tokenManager.saveEmail("a@b.c") }
    }

    @Test
    fun `failed login code verification keeps the old tokens`() = runTest {
        coEvery { api.verifyLoginCode(any()) } returns httpError(400, """{"message":"Invalid code"}""")

        repository.verifyLoginCode("a@b.c", "0000").toList()

        verify(exactly = 0) { tokenManager.saveToken(any()) }
        verify(exactly = 0) { tokenManager.saveAuthRefreshToken(any()) }
    }

    @Test
    fun `email verification persists tokens`() = runTest {
        coEvery { api.verifyEmail(any()) } returns httpOk(tokens())

        assertTrue(repository.verifyEmail("a@b.c", "1234").toList().last() is Resource.Success)

        verify(exactly = 1) { tokenManager.saveToken("access") }
    }

    @Test
    fun `logout clears local tokens before revoking the session`() = runTest {
        every { tokenManager.getAuthRefreshToken() } returns "refresh"
        coEvery { api.logout(any()) } returns httpOk(AuthServiceMessage("bye"))

        repository.logout()

        verify(exactly = 1) { tokenManager.clearToken() }
        coVerify(exactly = 1) { api.logout(any()) }
    }

    @Test
    fun `logout without a refresh token skips the network call`() = runTest {
        every { tokenManager.getAuthRefreshToken() } returns null

        repository.logout()

        verify(exactly = 1) { tokenManager.clearToken() }
        coVerify(exactly = 0) { api.logout(any()) }
    }

    @Test
    fun `logout survives a failing server call`() = runTest {
        every { tokenManager.getAuthRefreshToken() } returns "refresh"
        coEvery { api.logout(any()) } throws IOException("offline")

        repository.logout()

        verify(exactly = 1) { tokenManager.clearToken() }
    }

    @Test
    fun `isLoggedIn reflects the stored access token`() {
        every { tokenManager.getToken() } returns "access"
        assertTrue(repository.isLoggedIn())

        every { tokenManager.getToken() } returns null
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `deleteAccount reports success and failure`() = runTest {
        coEvery { api.deleteMe() } returnsMany listOf(
            httpOk(AuthServiceMessage("deleted")),
            httpError(500)
        )

        assertTrue(repository.deleteAccount())
        assertFalse(repository.deleteAccount())
    }

    @Test
    fun `deleteAccount survives a network failure`() = runTest {
        coEvery { api.deleteMe() } throws IOException("offline")

        assertFalse(repository.deleteAccount())
    }

    private fun tokens() = AuthServiceTokenResponse(
        accessToken = "access",
        refreshToken = "refresh",
        tokenType = "Bearer",
        expiresIn = 900,
        user = AuthServiceUser(
            id = "1",
            email = "a@b.c",
            username = "vovan",
            displayName = null,
            emailVerified = true
        )
    )
}
