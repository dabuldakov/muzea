package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.UnreadCountResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChatSessionRepositoryImplTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ChatSessionRepositoryImpl(api, auth)

    @Test
    fun `deleteAccount succeeds on 2xx`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAccount() } returns httpOk(Unit)

        assertTrue(repository.deleteAccount())
    }

    @Test
    fun `deleteAccount treats 401 and 404 as already deleted`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAccount() } returnsMany listOf(
            httpError(401),
            httpError(404)
        )

        assertTrue(repository.deleteAccount())
        assertTrue(repository.deleteAccount())
    }

    @Test
    fun `deleteAccount fails on server error`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAccount() } returns httpError(500)

        assertFalse(repository.deleteAccount())
    }

    @Test
    fun `deleteAccount fails without a chat token`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        assertFalse(repository.deleteAccount())
        coVerify(exactly = 0) { api.deleteAccount() }
    }

    @Test
    fun `deleteAccount fails on a network exception`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAccount() } throws IOException("offline")

        assertFalse(repository.deleteAccount())
    }

    @Test
    fun `sendHeartbeat is skipped without a token`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        assertFalse(repository.sendHeartbeat())
        coVerify(exactly = 0) { api.sendHeartbeat() }
    }

    @Test
    fun `sendHeartbeat reflects the server response`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.sendHeartbeat() } returnsMany listOf(httpOk(Unit), httpError(500))

        assertTrue(repository.sendHeartbeat())
        assertFalse(repository.sendHeartbeat())
    }

    @Test
    fun `unread count is mapped from the response`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getTotalUnreadCount() } returns httpOk(UnreadCountResponse(7))

        val last = repository.getTotalUnreadCount().toList().last()

        assertEquals(7L, (last as Resource.Success).data)
    }

    @Test
    fun `unread count reports an http failure`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getTotalUnreadCount() } returns httpError(500)

        val last = repository.getTotalUnreadCount().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load unread count"))
    }

    @Test
    fun `unread count reports the auth failure`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.getTotalUnreadCount().toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }
}
