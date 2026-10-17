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
    fun `requesting login code emits codeSent event`() = runTest {
        coEvery { authRepository.requestLoginCode("a@b.c") } returns flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        var codeSent = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.codeSent.collect { codeSent = true }
        }

        viewModel.requestLoginCode("a@b.c")
        collector.cancel()

        assertTrue(codeSent)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `verifying login code emits authenticated event`() = runTest {
        coEvery { authRepository.verifyLoginCode("a@b.c", "1234") } returns flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        var authenticated = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.authenticated.collect { authenticated = true }
        }

        viewModel.verifyLoginCode("a@b.c", "1234")
        collector.cancel()

        assertTrue(authenticated)
    }

    @Test
    fun `register emits codeSent event`() = runTest {
        coEvery { authRepository.register("a@b.c", "user", null) } returns flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        var codeSent = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.codeSent.collect { codeSent = true }
        }

        viewModel.register("a@b.c", "user", "")
        collector.cancel()

        assertTrue(codeSent)
    }

    @Test
    fun `failed request exposes error and can be consumed`() = runTest {
        coEvery { authRepository.requestLoginCode("a@b.c") } returns flowOf(Resource.Error("bad code"))
        val viewModel = AuthViewModel(authRepository)

        viewModel.requestLoginCode("a@b.c")
        assertEquals("bad code", viewModel.uiState.value.error)

        viewModel.consumeError()
        assertNull(viewModel.uiState.value.error)
    }
}
