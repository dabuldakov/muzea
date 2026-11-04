package com.example.muzea.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Проверяет логику [SecureStore] поверх in-memory хранилища.
 *
 * Реальный Keystore в JVM недоступен, поэтому приватное поле `prefs`
 * подменяется через рефлексию — это позволяет покрыть ветки, которые иначе
 * остались бы только в Android-рантайме.
 */
class SecureStoreTest {

    private lateinit var context: Context
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var prefsField: java.lang.reflect.Field

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        prefsField = SecureStore::class.java.getDeclaredField("prefs").apply { isAccessible = true }
        setPrefs(prefs)
    }

    @After
    fun tearDown() {
        setPrefs(null)
    }

    private fun setPrefs(value: android.content.SharedPreferences?) {
        prefsField.set(SecureStore, value)
    }

    @Test
    fun `auth token round trips`() {
        SecureStore.saveAuthToken(context, "access-1")

        assertEquals("access-1", SecureStore.loadAuthToken(context))

        SecureStore.clearAuthToken(context)
        assertNull(SecureStore.loadAuthToken(context))
    }

    @Test
    fun `auth refresh token round trips`() {
        SecureStore.saveAuthRefreshToken(context, "refresh-1")

        assertEquals("refresh-1", SecureStore.loadAuthRefreshToken(context))
    }

    @Test
    fun `chat token round trips`() {
        SecureStore.saveChatToken(context, "chat-1")

        assertEquals("chat-1", SecureStore.loadChatToken(context))

        SecureStore.clearChatToken(context)
        assertNull(SecureStore.loadChatToken(context))
    }

    @Test
    fun `clearPassword removes the legacy password`() {
        prefs.edit().putString("account_password", "secret").commit()

        SecureStore.clearPassword(context)

        assertNull(prefs.getString("account_password", null))
    }

    @Test
    fun `clearAll wipes every stored credential`() {
        SecureStore.saveAuthToken(context, "access")
        SecureStore.saveAuthRefreshToken(context, "refresh")
        SecureStore.saveChatToken(context, "chat")

        SecureStore.clearAll(context)

        assertNull(SecureStore.loadAuthToken(context))
        assertNull(SecureStore.loadAuthRefreshToken(context))
        assertNull(SecureStore.loadChatToken(context))
    }

    @Test
    fun `operations are safe when the encrypted store is unavailable`() {
        // Без доступного Keystore prefs(context) вернёт null, и все операции
        // должны молча ничего не делать.
        setPrefs(null)

        SecureStore.saveAuthToken(context, "access")
        SecureStore.saveAuthRefreshToken(context, "refresh")
        SecureStore.saveChatToken(context, "chat")
        SecureStore.clearAuthToken(context)
        SecureStore.clearChatToken(context)
        SecureStore.clearPassword(context)

        assertNull(SecureStore.loadAuthToken(context))
        assertNull(SecureStore.loadAuthRefreshToken(context))
        assertNull(SecureStore.loadChatToken(context))
    }
}
