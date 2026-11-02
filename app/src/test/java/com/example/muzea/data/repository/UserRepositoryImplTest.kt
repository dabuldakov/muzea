package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.UpdateUserRequest
import com.example.muzea.data.model.UserResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UserRepositoryImplTest {

    private val api = mockk<ApiService>()
    private val repository = UserRepositoryImpl(api)

    @Test
    fun `getCurrentUser maps the response`() = runTest {
        coEvery { api.getCurrentUser() } returns httpOk(user(7, "vovan"))

        val last = repository.getCurrentUser().toList().last()

        assertEquals("vovan", (last as Resource.Success).data!!.userName)
        assertEquals(7L, last.data!!.id)
    }

    @Test
    fun `getCurrentUser reports an http error`() = runTest {
        coEvery { api.getCurrentUser() } returns httpError(500)

        val last = repository.getCurrentUser().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load user"))
    }

    @Test
    fun `getCurrentUser reports a network error`() = runTest {
        coEvery { api.getCurrentUser() } throws IOException("offline")

        val last = repository.getCurrentUser().toList().last()

        assertEquals("Network error: offline", (last as Resource.Error).message)
    }

    @Test
    fun `updateUser sends the fields and maps the result`() = runTest {
        val request = slot<UpdateUserRequest>()
        coEvery { api.updateUser(7L, capture(request)) } returns httpOk(user(7, "vovan", "Вован"))

        val last = repository.updateUser(7L, "Вован", "vovan@mail.ru").toList().last()

        assertEquals("Вован", request.captured.fullName)
        assertEquals("vovan@mail.ru", request.captured.email)
        assertTrue(request.captured.enabled)
        assertEquals("Вован", (last as Resource.Success).data!!.fullName)
    }

    @Test
    fun `updateUser reports a failure`() = runTest {
        coEvery { api.updateUser(7L, any()) } returns httpError(409)

        val last = repository.updateUser(7L, null, null).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Update failed"))
    }

    @Test
    fun `deleteAccount treats 404 as already deleted`() = runTest {
        coEvery { api.deleteUser(7L) } returns httpError(404)

        val last = repository.deleteAccount(7L).toList().last()

        assertTrue(last is Resource.Success)
    }

    @Test
    fun `deleteAccount succeeds on 2xx`() = runTest {
        coEvery { api.deleteUser(7L) } returns httpOk(Unit)

        assertTrue(repository.deleteAccount(7L).toList().last() is Resource.Success)
    }

    @Test
    fun `deleteAccount reports a failure`() = runTest {
        coEvery { api.deleteUser(7L) } returns httpError(500)

        val last = repository.deleteAccount(7L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Delete failed"))
    }

    private fun user(id: Long, username: String, fullName: String? = null) = UserResponse(
        id = id,
        userName = username,
        email = "$username@mail.ru",
        fullName = fullName,
        avatarUrl = null,
        role = "USER",
        createdAt = null,
        enabled = true
    )
}
