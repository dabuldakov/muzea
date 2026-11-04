package com.example.muzea.utils

import com.example.muzea.core.DispatcherProvider
import com.example.muzea.data.repository.ChatAuthManager
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    @Test
    fun `sync registers the token delivered by Firebase`() {
        mockkStatic(FirebaseMessaging::class)
        try {
            val task = mockk<Task<String>>()
            every { FirebaseMessaging.getInstance().token } returns task
            every { task.addOnSuccessListener(any()) } answers {
                firstArg<OnSuccessListener<String>>().onSuccess("fcm-1")
                task
            }

            registrar.sync()

            verify(exactly = 1) { tokenManager.saveFcmToken("fcm-1") }
        } finally {
            unmockkStatic(FirebaseMessaging::class)
        }
    }
}
