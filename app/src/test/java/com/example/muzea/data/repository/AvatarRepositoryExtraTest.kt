package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.ChatUserResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvatarRepositoryExtraTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = AvatarRepositoryImpl(api, auth)

    @Test
    fun loadAvatarMapsAProfile() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMyProfile() } returns httpOk(
            ChatUserResponse(
                userUuid = "u1",
                username = null,
                firstName = null,
                lastName = null,
                fullName = null,
                avatarUrl = "/avatars/u1.png",
                isOnline = false
            )
        )

        val last = repository.loadAvatar().toList().last()

        assertEquals("/avatars/u1.png", (last as Resource.Success).data!!.avatarUrl)
    }

    @Test
    fun uploadAvatarReportsARejectedImage() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadAvatar(any()) } returns httpError(400)

        val last = repository.uploadAvatar(temporaryFolder.newFile(), "image/png").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Could not upload avatar"))
    }

    @Test
    fun uploadChatAvatarFailsWithoutAuth() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val results = repository.uploadChatAvatar(
            "chat-1", temporaryFolder.newFile(), "image/png"
        ).toList()

        assertTrue(results.last() is Resource.Error)
        coVerify(exactly = 0) { api.uploadChatAvatar(any(), any()) }
    }

    @Test
    fun uploadChatAvatarRetriesOnceAfterA401() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        val successBody = "/api/chats/chat-1/avatar"
            .toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { api.uploadChatAvatar("chat-1", any()) } returnsMany listOf(
            httpError(401),
            httpOk(successBody)
        )

        val last = repository.uploadChatAvatar(
            "chat-1", temporaryFolder.newFile(), "image/png"
        ).toList().last()

        assertEquals("/api/chats/chat-1/avatar", (last as Resource.Success).data)
        coVerify(exactly = 2) { api.uploadChatAvatar("chat-1", any()) }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun uploadChatAvatarReportsARejectedImage() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadChatAvatar("chat-1", any()) } returns httpError<ResponseBody>(400)

        val last = repository.uploadChatAvatar(
            "chat-1", temporaryFolder.newFile(), "image/png"
        ).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to upload avatar"))
    }

    @Test
    fun uploadChatAvatarPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadChatAvatar("chat-1", any()) } throws CancellationException("stop")

        var cancelled = false
        try {
            repository.uploadChatAvatar(
                "chat-1", temporaryFolder.newFile(), "image/png"
            ).toList()
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun uploadChatAvatarReportsANetworkError() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.uploadChatAvatar("chat-1", any()) } throws java.io.IOException("offline")

        val last = repository.uploadChatAvatar(
            "chat-1", temporaryFolder.newFile(), "image/png"
        ).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun avatarRequestPropagatesCancellation() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getMyProfile() } throws CancellationException("stop")

        var cancelled = false
        try {
            repository.loadAvatar().toList()
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
