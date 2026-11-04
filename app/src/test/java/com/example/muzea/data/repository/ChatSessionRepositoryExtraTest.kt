package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проброс отмены (CancellationException) во всех методах
 * [ChatSessionRepositoryImpl] — ветки, которые обычные тесты не задевают.
 */
class ChatSessionRepositoryExtraTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ChatSessionRepositoryImpl(api, auth)

    private inline fun cancellationThrown(block: () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    @Test
    fun deleteAccountPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAccount() } throws CancellationException("stop")

        assertTrue(cancellationThrown { repository.deleteAccount() })
    }

    @Test
    fun sendHeartbeatPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.sendHeartbeat() } throws CancellationException("stop")

        assertTrue(cancellationThrown { repository.sendHeartbeat() })
    }

    @Test
    fun unreadCountPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getTotalUnreadCount() } throws CancellationException("stop")

        assertTrue(cancellationThrown { repository.getTotalUnreadCount().toList() })
    }
}
