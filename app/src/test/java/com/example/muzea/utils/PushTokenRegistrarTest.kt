package com.example.muzea.utils

import com.example.muzea.core.DispatcherProvider
import com.example.muzea.data.repository.ChatAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistrarTest {

    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val chatAuthManager = mockk<ChatAuthManager>(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
    private val registrar = PushTokenRegistrar(tokenManager, chatAuthManager, dispatchers)

    @Test
    fun `register stores the token and triggers chat registration`() {
        coEvery { chatAuthManager.isAuthenticated() } returns true

        registrar.register("fcm-token")

        verify(exactly = 1) { tokenManager.saveFcmToken("fcm-token") }
        verify(exactly = 1) { tokenManager.saveRegisteredFcmToken("") }
        coVerify(exactly = 1) { chatAuthManager.isAuthenticated() }
    }

}
