package com.example.muzea.data.api

import com.example.muzea.data.repository.AuthTokenRefresher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenRefreshInterceptorTest {

    private lateinit var server: MockWebServer
    private val refresher = mockk<AuthTokenRefresher>(relaxed = true)
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder()
            .addInterceptor(TokenRefreshInterceptor(refresher))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 triggers a refresh and one retry with the new token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        every { refresher.currentToken() } returns "old"
        every { refresher.refreshToken() } returns "new"

        val response = client.newCall(request("/api/chats", "old")).execute()

        assertEquals(200, response.code)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer old", first.getHeader("Authorization"))
        assertEquals("Bearer new", second.getHeader("Authorization"))
        assertEquals("1", second.getHeader("X-Muzea-Retry"))
        verify(exactly = 1) { refresher.refreshToken() }
        response.close()
    }

    @Test
    fun `403 is treated like an expired token`() {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(200))
        every { refresher.currentToken() } returns "old"
        every { refresher.refreshToken() } returns "new"

        assertEquals(200, client.newCall(request("/api/news", "old")).execute().code)
    }

    @Test
    fun `auth endpoints are never retried`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(request("/auth/token/refresh", "old")).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
        verify(exactly = 0) { refresher.refreshToken() }
    }

    @Test
    fun `a retried request is not retried again`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        every { refresher.currentToken() } returns "old"
        every { refresher.refreshToken() } returns "new"

        val request = request("/api/chats", "old").newBuilder()
            .header("X-Muzea-Retry", "1")
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
        verify(exactly = 0) { refresher.refreshToken() }
    }

    @Test
    fun `successful responses are returned untouched`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request("/api/chats", "old")).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
        verify(exactly = 0) { refresher.refreshToken() }
    }

    @Test
    fun `a token refreshed by a parallel request is reused without refreshing again`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))
        every { refresher.currentToken() } returns "already-new"
        every { refresher.refreshToken() } returns null

        val response = client.newCall(request("/api/chats", "old")).execute()

        assertEquals(200, response.code)
        assertEquals("Bearer already-new", server.takeRequest().let { server.takeRequest() }.getHeader("Authorization"))
        verify(exactly = 0) { refresher.refreshToken() }
    }

    @Test
    fun `failed refresh returns the original 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        every { refresher.currentToken() } returns "old"
        every { refresher.refreshToken() } returns null

        val response = client.newCall(request("/api/chats", "old")).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `response body is readable after a retry`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        every { refresher.currentToken() } returns "old"
        every { refresher.refreshToken() } returns "new"

        val response = client.newCall(request("/api/chats", "old")).execute()

        assertNotNull(response.body)
        assertEquals("hello", response.body!!.string())
        response.close()
    }

    private fun request(path: String, token: String?): Request =
        Request.Builder()
            .url(server.url(path))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
}
