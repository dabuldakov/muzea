package com.example.muzea.data.api

import com.example.muzea.utils.TokenManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private val tokenManager = mockk<TokenManager>()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds a bearer header when a token exists`() = runTest {
        every { tokenManager.getToken() } returns "access"
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(request()).execute()

        assertEquals("Bearer access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `does not add a header without a token`() = runTest {
        every { tokenManager.getToken() } returns null
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(request()).execute()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `does not add a header for an empty token`() = runTest {
        every { tokenManager.getToken() } returns ""
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(request()).execute()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    private fun request() = Request.Builder().url(server.url("/api/ping")).build()
}
