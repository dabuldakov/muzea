package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.core.Resource
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class ChatAvatarRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = AvatarRepositoryImpl(api, auth)

    @Test
    fun `upload retries expired chat token and returns avatar URL`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadAvatar(any()) } returnsMany listOf(
            Response.error(401, "expired".toResponseBody()),
            Response.success(AvatarResponse("/api/avatars/user/version.png"))
        )
        val file = temporaryFolder.newFile("avatar.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val results = repository.uploadAvatar(file, "image/png").toList()
        assertTrue(results.first() is Resource.Loading)
        assertEquals("/api/avatars/user/version.png", results.last().data?.avatarUrl)
        coVerify(exactly = 2) { api.uploadAvatar(match {
            it.headers?.get("Content-Disposition")?.contains("name=\"file\"") == true
        }) }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `failed authentication does not upload`() = runTest {
        coEvery { auth.isAuthenticated() } returns false
        val result = repository.uploadAvatar(temporaryFolder.newFile(), "image/png").toList().last()
        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { api.uploadAvatar(any()) }
    }

    @Test
    fun `delete accepts empty response and clears avatar`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.deleteAvatar() } returns Response.success<Unit>(204, null)
        val result = repository.deleteAvatar().toList().last()
        assertTrue(result is Resource.Success)
        assertNull(result.data?.avatarUrl)
    }
}
