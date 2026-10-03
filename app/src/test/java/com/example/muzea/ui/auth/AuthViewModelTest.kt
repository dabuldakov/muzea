package com.example.muzea.ui.auth

import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()

    @Test
    fun `successful login emits authenticated event`() = runTest {
        coEvery { authRepository.login("u", "p") } returns flowOf(Resource.Success("token"))
        val viewModel = AuthViewModel(authRepository)

        var authenticated = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.authenticated.collect { authenticated = true }
        }

        viewModel.login("u", "p")
        collector.cancel()

        assertTrue(authenticated)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed login exposes error and can be consumed`() = runTest {
        coEvery { authRepository.login("u", "p") } returns flowOf(Resource.Error("bad credentials"))
        val viewModel = AuthViewModel(authRepository)

        viewModel.login("u", "p")
        assertEquals("bad credentials", viewModel.uiState.value.error)

        viewModel.consumeError()
        assertNull(viewModel.uiState.value.error)
    }
}
