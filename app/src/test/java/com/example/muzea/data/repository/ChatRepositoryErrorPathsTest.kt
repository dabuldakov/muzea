package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Ветки ошибок/отмены [ChatRepositoryImpl], которые не задевают тесты happy
 * path: отсутствие авторизации, неуспешный ответ, сетевое исключение и проброс
 * [CancellationException] в каждом публичном методе.
 */
class ChatRepositoryErrorPathsTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ChatRepositoryImpl(api, auth)

    private suspend fun cancels(block: suspend () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    // --- loadChats ---

    @Test
    fun loadChatsFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.loadChats().toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun loadChatsPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChats() } throws CancellationException("stop")

        assertTrue(cancels { repository.loadChats().toList() })
    }

    @Test
    fun loadChatsReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChats() } throws IOException("offline")

        val last = repository.loadChats().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    // --- loadChatParticipants ---

    @Test
    fun loadChatParticipantsFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.loadChatParticipants("c1").toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun loadChatParticipantsReportsAFailure() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChatParticipants("c1") } returns httpError(500)

        val last = repository.loadChatParticipants("c1").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load participants"))
    }

    @Test
    fun loadChatParticipantsPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChatParticipants("c1") } throws CancellationException("stop")

        assertTrue(cancels { repository.loadChatParticipants("c1").toList() })
    }

    @Test
    fun loadChatParticipantsReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getChatParticipants("c1") } throws IOException("offline")

        val last = repository.loadChatParticipants("c1").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    // --- createPrivateChat ---

    @Test
    fun createPrivateChatFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.createPrivateChat("u1").toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun createPrivateChatReportsAFailure() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createPrivateChat(any()) } returns httpError(500)

        val last = repository.createPrivateChat("u1").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to create chat"))
    }

    @Test
    fun createPrivateChatPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createPrivateChat(any()) } throws CancellationException("stop")

        assertTrue(cancels { repository.createPrivateChat("u1").toList() })
    }

    @Test
    fun createPrivateChatReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createPrivateChat(any()) } throws IOException("offline")

        val last = repository.createPrivateChat("u1").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    // --- createGroupChat ---

    @Test
    fun createGroupChatFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.createGroupChat("team", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun createGroupChatReportsAFailure() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createGroupChat(any()) } returns httpError(500)

        val last = repository.createGroupChat("team", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to create group chat"))
    }

    @Test
    fun createGroupChatPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createGroupChat(any()) } throws CancellationException("stop")

        assertTrue(cancels { repository.createGroupChat("team", listOf("u1")).toList() })
    }

    @Test
    fun createGroupChatReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.createGroupChat(any()) } throws IOException("offline")

        val last = repository.createGroupChat("team", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    // --- addGroupParticipants ---

    @Test
    fun addGroupParticipantsFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.addGroupParticipants("c1", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun addGroupParticipantsReportsAFailure() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.addGroupParticipants("c1", any()) } returns httpError(500)

        val last = repository.addGroupParticipants("c1", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to add members"))
    }

    @Test
    fun addGroupParticipantsPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.addGroupParticipants("c1", any()) } throws CancellationException("stop")

        assertTrue(cancels { repository.addGroupParticipants("c1", listOf("u1")).toList() })
    }

    @Test
    fun addGroupParticipantsReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.addGroupParticipants("c1", any()) } throws IOException("offline")

        val last = repository.addGroupParticipants("c1", listOf("u1")).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }
}
