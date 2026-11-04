package com.example.muzea.ui.profile

import android.app.Application
import com.example.muzea.core.Resource
import com.example.muzea.data.model.AuthServiceUser
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.domain.model.User
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.UserRepository
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.TokenManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Дополнительные тесты ProfileViewModel: email из IdP, отсутствие auth-пользователя
 * и состояние загрузки профиля.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val chatSessionRepository = mockk<ChatSessionRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val tokenManager = mockk<TokenManager>(relaxed = true)

    private fun viewModel() = ProfileViewModel(
        application,
        userRepository,
        chatSessionRepository,
        authRepository,
        tokenManager
    )

    private fun user(id: Long = 7L) = User(
        id = id,
        userName = "vovan",
        fullName = null,
        avatarUrl = null,
        role = "USER",
        createdAt = null,
        enabled = true
    )

    @Test
    fun `loadUserProfile publishes the email from the identity provider`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { authRepository.getCurrentAuthUser() } returns AuthServiceUser(
            id = "id-1",
            email = "vovan@muzea.su",
            username = "vovan",
            displayName = null,
            emailVerified = true
        )
        val viewModel = viewModel()

        viewModel.loadUserProfile()

        assertEquals("vovan@muzea.su", viewModel.profileState.value.email)
        assertEquals("vovan", viewModel.profileState.value.user?.userName)
        assertFalse(viewModel.profileState.value.isLoading)
    }

    @Test
    fun `loadUserProfile tolerates a missing identity provider user`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { authRepository.getCurrentAuthUser() } returns null
        val viewModel = viewModel()

        viewModel.loadUserProfile()

        assertNull(viewModel.profileState.value.email)
        assertNull(viewModel.profileState.value.error)
    }

    @Test
    fun `loadUserProfile exposes the loading state`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { userRepository.getCurrentUser() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(user(9)))
        }
        coEvery { authRepository.getCurrentAuthUser() } returns null
        val viewModel = viewModel()

        viewModel.loadUserProfile()
        assertTrue(viewModel.profileState.value.isLoading)

        gate.complete(Unit)
        assertFalse(viewModel.profileState.value.isLoading)
        assertEquals("vovan", viewModel.profileState.value.user?.userName)
    }
}
