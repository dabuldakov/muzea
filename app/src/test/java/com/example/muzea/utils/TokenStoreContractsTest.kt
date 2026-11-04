package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Проверяет контракт интерфейсов-хранилищ через in-memory реализации. */
class TokenStoreContractsTest {

    private class FakeFcmTokenStore : FcmTokenStore {
        private var storedDeviceId: String? = "device-1"
        private var storedDeviceName: String? = "Pixel"
        private var storedDeviceType: String? = "ANDROID"
        private var storedFcmToken: String? = null
        private var storedRegisteredFcmToken: String? = null

        override fun getDeviceId(): String? = storedDeviceId
        override fun getDeviceName(): String? = storedDeviceName
        override fun getDeviceType(): String? = storedDeviceType
        override fun getFcmToken(): String? = storedFcmToken
        override fun saveFcmToken(token: String) { storedFcmToken = token }
        override fun getRegisteredFcmToken(): String? = storedRegisteredFcmToken
        override fun saveRegisteredFcmToken(token: String) { storedRegisteredFcmToken = token }
    }

    private class FakeChatTokenStore : ChatTokenStore {
        private var storedUsername: String? = "user"
        private var storedEmail: String? = "a@b.c"
        private var storedChatToken: String? = null
        private var storedChatTokenUser: String? = null

        override fun getUsername(): String? = storedUsername
        override fun getEmail(): String? = storedEmail
        override fun getChatToken(): String? = storedChatToken
        override fun getChatTokenUser(): String? = storedChatTokenUser
        override fun saveChatToken(token: String) { storedChatToken = token }
        override fun saveChatTokenUser(username: String) { storedChatTokenUser = username }
        override fun clearChatToken() { storedChatToken = null; storedChatTokenUser = null }
    }

    @Test
    fun `fcm token store round trips tokens and exposes device info`() {
        val store = FakeFcmTokenStore()

        assertEquals("device-1", store.getDeviceId())
        assertEquals("Pixel", store.getDeviceName())
        assertEquals("ANDROID", store.getDeviceType())
        assertNull(store.getFcmToken())
        assertNull(store.getRegisteredFcmToken())

        store.saveFcmToken("fcm-1")
        store.saveRegisteredFcmToken("fcm-0")

        assertEquals("fcm-1", store.getFcmToken())
        assertEquals("fcm-0", store.getRegisteredFcmToken())
    }

    @Test
    fun `chat token store round trips credentials and clears them`() {
        val store = FakeChatTokenStore()

        assertEquals("user", store.getUsername())
        assertEquals("a@b.c", store.getEmail())
        assertNull(store.getChatToken())

        store.saveChatToken("chat-1")
        store.saveChatTokenUser("user-1")
        assertEquals("chat-1", store.getChatToken())
        assertEquals("user-1", store.getChatTokenUser())

        store.clearChatToken()
        assertNull(store.getChatToken())
        assertNull(store.getChatTokenUser())
    }
}
