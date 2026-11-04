package com.example.muzea.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenManagerTest {

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
    fun `device id is generated once and then reused`() {
        val first = TokenManager(context).getDeviceId()
        val second = TokenManager(context).getDeviceId()

        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }

    @Test
    fun `plain profile fields are persisted`() {
        val manager = TokenManager(context)

        manager.saveUsername("vovan")
        manager.saveEmail("vovan@mail.ru")
        manager.saveChatUserUuid("uuid-1")
        manager.saveChatTokenUser("vovan")

        val restored = TokenManager(context)
        assertEquals("vovan", restored.getUsername())
        assertEquals("vovan@mail.ru", restored.getEmail())
        assertEquals("uuid-1", restored.getChatUserUuid())
        assertEquals("vovan", restored.getChatTokenUser())
    }

    @Test
    fun `fcm tokens are persisted separately`() {
        val manager = TokenManager(context)

        manager.saveFcmToken("fcm-1")
        manager.saveRegisteredFcmToken("fcm-0")

        assertEquals("fcm-1", TokenManager(context).getFcmToken())
        assertEquals("fcm-0", TokenManager(context).getRegisteredFcmToken())
    }

    @Test
    fun `legacy plaintext password is purged and never stored`() {
        prefs.edit().putString("password", "secret").commit()

        TokenManager(context)

        verify(exactly = 1) { SecureStore.clearPassword(context) }
        assertFalse(prefs.contains("password"))
    }

    @Test
    fun `legacy plaintext tokens are moved into secure storage`() {
        prefs.edit()
            .putString("auth_token", "auth")
            .putString("chat_token", "chat")
            .commit()

        TokenManager(context)

        verify(exactly = 1) { SecureStore.saveAuthToken(context, "auth") }
        verify(exactly = 1) { SecureStore.saveChatToken(context, "chat") }
        assertFalse(prefs.contains("auth_token"))
        assertFalse(prefs.contains("chat_token"))
    }

    @Test
    fun `secure accessors delegate to the encrypted store`() {
        every { SecureStore.loadAuthToken(any()) } returns "auth"
        every { SecureStore.loadAuthRefreshToken(any()) } returns "refresh"
        every { SecureStore.loadChatToken(any()) } returns "chat"

        val manager = TokenManager(context)

        assertEquals("auth", manager.getToken())
        assertEquals("refresh", manager.getAuthRefreshToken())
        assertEquals("chat", manager.getChatToken())
    }

    @Test
    fun `clearChatToken removes both the token and its owner`() {
        val manager = TokenManager(context)
        manager.saveChatTokenUser("vovan")

        manager.clearChatToken()

        verify(exactly = 1) { SecureStore.clearChatToken(context) }
        assertNull(TokenManager(context).getChatTokenUser())
    }

    @Test
    fun `clearToken wipes profile fields and secure storage`() {
        val manager = TokenManager(context)
        manager.saveUsername("vovan")
        manager.saveChatUserUuid("uuid-1")

        manager.clearToken()

        verify(exactly = 1) { SecureStore.clearAll(context) }
        assertNull(TokenManager(context).getUsername())
        assertNull(TokenManager(context).getChatUserUuid())
    }

    @Test
    fun `clearAll wipes every preference`() {
        val manager = TokenManager(context)
        manager.saveUsername("vovan")
        manager.saveFcmToken("fcm")

        manager.clearAll()

        verify(exactly = 1) { SecureStore.clearAll(context) }
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `device description uses manufacturer and model`() {
        val manager = TokenManager(context)

        assertEquals("ANDROID", manager.getDeviceType())
        assertTrue(manager.getDeviceName().isNotBlank())
    }
}
