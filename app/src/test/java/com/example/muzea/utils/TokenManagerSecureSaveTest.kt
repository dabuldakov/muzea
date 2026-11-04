package com.example.muzea.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Делегаты TokenManager в шифрованное хранилище для методов записи
 * (access/refresh/chat токены) — [TokenManagerTest] покрывает только чтение.
 */
class TokenManagerSecureSaveTest {

    private lateinit var context: Context
    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        context = mockk()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) } returns prefs
        mockkObject(SecureStore)
    }

    @After
    fun tearDown() {
        unmockkObject(SecureStore)
    }

    @Test
    fun saveTokenGoesThroughSecureStore() {
        TokenManager(context).saveToken("auth-1")

        verify(exactly = 1) { SecureStore.saveAuthToken(context, "auth-1") }
    }

    @Test
    fun saveAuthRefreshTokenGoesThroughSecureStore() {
        TokenManager(context).saveAuthRefreshToken("refresh-1")

        verify(exactly = 1) { SecureStore.saveAuthRefreshToken(context, "refresh-1") }
    }

    @Test
    fun saveChatTokenGoesThroughSecureStore() {
        TokenManager(context).saveChatToken("chat-1")

        verify(exactly = 1) { SecureStore.saveChatToken(context, "chat-1") }
    }
}
