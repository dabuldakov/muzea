package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.data.IntegrationTestClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Интеграционный тест отметок по сообщению: реальный Retrofit поверх
 * MockWebServer проверяет путь, JSON-контракт и разбор пустых значений.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageReceiptTimesIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun repository() = MessageRepositoryImpl(
        IntegrationTestClient.chatApi(server),
        auth,
        GifCache(File.createTempFile("gif-cache", ".json").apply { delete() }),
        context
    )

    @Test
    fun `loads exact delivery and read times from the contract`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"userUuid":"u1","deliveredAt":"2026-11-14T03:55:17Z","readAt":"2026-11-14T03:55:20Z"},
                  {"userUuid":"u2","deliveredAt":null,"readAt":null}
                ]
                """.trimIndent()
            )
        )

        val times = repository().loadMessageReceiptTimes("chat-1", "m1")

        assertEquals(2, times?.size)
        val first = times!!.first { it.userUuid == "u1" }
        assertEquals("2026-11-14T03:55:17Z", first.deliveredAt)
        assertEquals("2026-11-14T03:55:20Z", first.readAt)

        // История без отметок: время не подставляем.
        val second = times.first { it.userUuid == "u2" }
        assertNull(second.deliveredAt)
        assertNull(second.readAt)

        val request = server.takeRequest()
        assertEquals("/api/messages/chat-1/status/m1", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `server error returns null`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(repository().loadMessageReceiptTimes("chat-1", "m1"))
    }

    @Test
    fun `unauthorized request is retried once`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"userUuid":"u1","deliveredAt":"2026-11-14T03:55:17Z","readAt":null}]"""
            )
        )

        val times = repository().loadMessageReceiptTimes("chat-1", "m1")

        assertEquals("2026-11-14T03:55:17Z", times?.single()?.deliveredAt)
        coVerify(exactly = 2) { auth.isAuthenticated() }
        verify(exactly = 1) { auth.invalidate() }
        assertEquals(2, server.requestCount)
    }
}
