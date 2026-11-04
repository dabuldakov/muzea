package com.example.muzea.ui.auth

import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Дополнительные тесты AuthViewModel: повторная отправка кода, подтверждение
 * email, разлогин, состояния загрузки и сброс ошибки новым запросом.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()

    @Test
    fun `resendVerification emits codeSent`() = runTest {
        coEvery { authRepository.resendVerification("a@b.c") } returns flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        var codeSent = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.codeSent.collect { codeSent = true }
        }

        viewModel.resendVerification("a@b.c")
        collector.cancel()

        assertTrue(codeSent)
        coVerify(exactly = 1) { authRepository.resendVerification("a@b.c") }
    }

    @Test
    fun `verifyEmail emits authenticated`() = runTest {
        coEvery { authRepository.verifyEmail("a@b.c", "1234") } returns flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        var authenticated = false
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.authenticated.collect { authenticated = true }
        }

        viewModel.verifyEmail("a@b.c", "1234")
        collector.cancel()

        assertTrue(authenticated)
    }

    @Test
    fun `register forwards a non-blank display name`() = runTest {
        coEvery { authRepository.register("a@b.c", "user", "Vovan") } returns
            flowOf(Resource.Success(Unit))
        val viewModel = AuthViewModel(authRepository)

        viewModel.register("a@b.c", "user", "Vovan")

        coVerify(exactly = 1) { authRepository.register("a@b.c", "user", "Vovan") }
    }

    @Test
    fun `logout delegates to the repository`() = runTest {
        coJustRun { authRepository.logout() }
        val viewModel = AuthViewModel(authRepository)

        viewModel.logout()

        coVerify(exactly = 1) { authRepository.logout() }
    }

    @Test
    fun `loading state is exposed and the previous error is cleared`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val first: Flow<Resource<Unit>> = flowOf(Resource.Error("boom"))
        val second: Flow<Resource<Unit>> = flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(Unit))
        }
        coEvery { authRepository.requestLoginCode("a@b.c") } returnsMany listOf(first, second)
        val viewModel = AuthViewModel(authRepository)

        viewModel.requestLoginCode("a@b.c")
        assertEquals("boom", viewModel.uiState.value.error)

        viewModel.requestLoginCode("a@b.c")
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }
}
