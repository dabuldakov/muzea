package com.example.muzea.data.repository

import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.utils.ChatTokenStore
import com.example.muzea.utils.FcmTokenStore
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Интеграционный тест аутентификации чата против MockWebServer.
 * Проверяет, что в запросы login/register попадают device-поля с теми же
 * JSON-ключами, которые принимает живой сервер, и что токен сохраняется.
 */
class ChatAuthManagerIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    private class FakeStore : ChatTokenStore, FcmTokenStore {
        private var usernameValue: String? = "alice"
        private var passwordValue: String? = "pass12345"
        private var chatTokenValue: String? = null
        private var chatTokenUserValue: String? = null

        override fun getUsername() = usernameValue
        override fun getPassword() = passwordValue
        override fun getEmail(): String? = "alice@example.com"
        override fun getChatToken() = chatTokenValue
        override fun getChatTokenUser() = chatTokenUserValue
        override fun saveChatToken(token: String) { chatTokenValue = token }
        override fun saveChatTokenUser(name: String) { chatTokenUserValue = name }
        override fun clearChatToken() { chatTokenValue = null; chatTokenUserValue = null }

        override fun getDeviceId() = "device-test-001"
        override fun getDeviceName() = "Test Phone"
        override fun getDeviceType() = "ANDROID"
        override fun getFcmToken(): String? = null
        override fun saveFcmToken(token: String) {}
        override fun getRegisteredFcmToken(): String? = null
        override fun saveRegisteredFcmToken(token: String) {}
    }

    private val loginResponse = """
        {"token":"chat-jwt-token","refreshToken":"refresh-1",
         "userUuid":"f8ea45c4-6e3c-4b85-8534-c3f3f08d079a",
         "username":"alice","email":"alice@example.com","avatarUrl":null}
    """.trimIndent()

    @Test
    fun `login sends device fields and stores token`() = runTest {
        val store = FakeStore()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(loginResponse)
        )

        val manager = ChatAuthManager(IntegrationTestClient.chatApi(server), store)

        assertTrue(manager.isAuthenticated())

        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("alice", body.get("username").asString)
        assertEquals("pass12345", body.get("password").asString)
        assertEquals("device-test-001", body.get("deviceId").asString)
        assertEquals("Test Phone", body.get("deviceName").asString)
        assertEquals("ANDROID", body.get("deviceType").asString)

        assertEquals("chat-jwt-token", store.getChatToken())
        assertEquals("alice", store.getChatTokenUser())
    }

    @Test
    fun `falls back to register when login fails`() = runTest {
        val store = FakeStore()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(loginResponse)
        )

        val manager = ChatAuthManager(IntegrationTestClient.chatApi(server), store)

        assertTrue(manager.isAuthenticated())

        server.takeRequest() // /api/auth/login
        val registerRequest = server.takeRequest()
        assertEquals("/api/auth/register", registerRequest.path)
        val body = JsonParser().parse(registerRequest.body.readUtf8()).asJsonObject
        assertEquals("alice", body.get("username").asString)
        assertEquals("alice@example.com", body.get("email").asString)
        assertEquals("pass12345", body.get("password").asString)
        assertEquals("device-test-001", body.get("deviceId").asString)
        assertEquals("Test Phone", body.get("deviceName").asString)
        assertEquals("ANDROID", body.get("deviceType").asString)

        assertEquals("chat-jwt-token", store.getChatToken())
    }

    @Test
    fun `token owned by another user is cleared before re-login`() = runTest {
        val store = FakeStore()
        store.saveChatToken("stale-token")
        store.saveChatTokenUser("bob")
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(loginResponse)
        )

        val manager = ChatAuthManager(IntegrationTestClient.chatApi(server), store)

        assertTrue(manager.isAuthenticated())

        server.takeRequest()
        assertEquals("chat-jwt-token", store.getChatToken())
        assertEquals("alice", store.getChatTokenUser())
    }

    @Test
    fun `reports conflict when register fails with 409`() = runTest {
        val store = FakeStore()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(409))

        val manager = ChatAuthManager(IntegrationTestClient.chatApi(server), store)

        assertFalse(manager.isAuthenticated())
        assertNotNull(manager.lastFailureMessage)
        assertTrue(manager.lastFailureMessage!!.contains("already exists"))
        assertTrue(manager.lastFailureMessage!!.contains("alice"))

        server.takeRequest() // login
        val register = server.takeRequest()
        assertEquals("/api/auth/register", register.path)
        val body = JsonParser().parse(register.body.readUtf8()).asJsonObject
        assertEquals("alice", body.get("username").asString)
    }
}