package com.example.muzea.ui.profile

import android.app.Application
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.domain.model.User
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.UserRepository
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.TokenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val chatSessionRepository = mockk<ChatSessionRepository>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val tokenManager = mockk<TokenManager>(relaxed = true)

    private fun viewModel() = ProfileViewModel(
        application,
        userRepository,
        chatSessionRepository,
        authRepository,
        tokenManager
    )

    @Before
    fun setUp() {
        mockkObject(ConsentManager)
        justRun { ConsentManager.revoke(any()) }
        every { application.getString(R.string.delete_account_chat_failed) } returns "chat failed"
    }

    @After
    fun tearDown() {
        unmockkObject(ConsentManager)
    }

    @Test
    fun `loadUserProfile publishes the user and remembers the id`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns
            flowOf(Resource.Loading(), Resource.Success(user(7)))
        val viewModel = viewModel()

        viewModel.loadUserProfile()

        assertEquals("vovan", viewModel.profileState.value.user?.userName)
        assertFalse(viewModel.profileState.value.isLoading)

        // id из профиля используется при сохранении изменений.
        coEvery { userRepository.updateUser(7L, any(), any(), any()) } returns
            flowOf(Resource.Success(user(7)))
        viewModel.updateUserProfile("Вован", "vovan@mail.ru")
        coVerify { userRepository.updateUser(7L, "Вован", "vovan@mail.ru", true) }
    }

    @Test
    fun `loadUserProfile exposes the error and keeps loading off`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns
            flowOf(Resource.Loading(), Resource.Error("boom"))
        val viewModel = viewModel()

        viewModel.loadUserProfile()

        assertEquals("boom", viewModel.profileState.value.error)
        assertFalse(viewModel.profileState.value.isLoading)
        assertNull(viewModel.profileState.value.user)
    }

    @Test
    fun `updateUserProfile forwards the result`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { userRepository.updateUser(7L, any(), any(), any()) } returns
            flowOf(Resource.Success(user(7, fullName = "Вован")))
        val viewModel = viewModel()
        viewModel.loadUserProfile()

        val results = mutableListOf<Resource<User>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.updateProfileResult.collect { results += it }
        }
        viewModel.updateUserProfile("Вован", "vovan@mail.ru")

        assertEquals("Вован", (results.last() as Resource.Success).data!!.fullName)
        collector.cancel()
    }

    @Test
    fun `successful deletion clears tokens and consent`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { chatSessionRepository.deleteAccount() } returns true
        coEvery { userRepository.deleteAccount(7L) } returns flowOf(
            Resource.Loading(),
            Resource.Success(Unit)
        )
        coEvery { authRepository.deleteAccount() } returns true
        val viewModel = viewModel()
        viewModel.loadUserProfile()

        val results = mutableListOf<Resource<Unit>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.deleteAccountResult.collect { results += it }
        }
        viewModel.deleteAccount()

        assertTrue(results.last() is Resource.Success)
        verify(exactly = 1) { tokenManager.clearAll() }
        verify(exactly = 1) { ConsentManager.revoke(application) }
        coVerify(exactly = 1) { authRepository.deleteAccount() }
        collector.cancel()
    }

    @Test
    fun `chat deletion failure keeps local credentials`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { chatSessionRepository.deleteAccount() } returns false
        coEvery { userRepository.deleteAccount(7L) } returns flowOf(Resource.Success(Unit))
        val viewModel = viewModel()
        viewModel.loadUserProfile()

        val results = mutableListOf<Resource<Unit>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.deleteAccountResult.collect { results += it }
        }
        viewModel.deleteAccount()

        assertEquals("chat failed", (results.last() as Resource.Error).message)
        verify(exactly = 0) { tokenManager.clearAll() }
        verify(exactly = 0) { ConsentManager.revoke(any()) }
        collector.cancel()
    }

    @Test
    fun `main backend deletion failure keeps local credentials`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns flowOf(Resource.Success(user(7)))
        coEvery { chatSessionRepository.deleteAccount() } returns true
        coEvery { userRepository.deleteAccount(7L) } returns flowOf(Resource.Error("500"))
        val viewModel = viewModel()
        viewModel.loadUserProfile()

        val results = mutableListOf<Resource<Unit>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.deleteAccountResult.collect { results += it }
        }
        viewModel.deleteAccount()

        assertTrue(results.last() is Resource.Error)
        verify(exactly = 0) { tokenManager.clearAll() }
        collector.cancel()
    }

    @Test
    fun `logout delegates to the auth repository`() = runTest {
        val viewModel = viewModel()

        viewModel.logout()

        coVerify(exactly = 1) { authRepository.logout() }
    }

    private fun user(id: Long, fullName: String? = null) = User(
        id = id,
        userName = "vovan",
        email = "vovan@mail.ru",
        fullName = fullName,
        avatarUrl = null,
        role = "USER",
        createdAt = null,
        enabled = true
    )
}
