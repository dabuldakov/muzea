package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Ветки ошибок/отмены [ContactRepositoryImpl]: проброс отмены в загрузке и
 * статусах, плюс отсутствие авторизации и сетевой сбой при добавлении контакта.
 */
class ContactRepositoryErrorPathsTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ContactRepositoryImpl(api, auth)

    private suspend fun cancels(block: suspend () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    @Test
    fun loadContactsPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getContacts() } throws CancellationException("stop")

        assertTrue(cancels { repository.loadContacts().toList() })
    }

    @Test
    fun addContactFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.addContactByUsername("alice").toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
    }

    @Test
    fun addContactPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getUserByUsername("alice") } throws CancellationException("stop")

        assertTrue(cancels { repository.addContactByUsername("alice").toList() })
    }

    @Test
    fun addContactReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getUserByUsername("alice") } throws IOException("offline")

        val last = repository.addContactByUsername("alice").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun loadPresencePropagatesCancellation() = runTest {
        coEvery { api.getPresence(listOf("u1", "u2")) } throws CancellationException("stop")

        assertTrue(cancels { repository.loadPresence(listOf("u1", "u2")) })
    }
}
