package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.ChatAuthResponse
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.PageResponse
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.utils.ChatTokenStore
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatAuthManagerTest {

    private class FakeApi(
        var loginResult: Response<ChatAuthResponse>? = null,
        var registerResult: Response<ChatAuthResponse>? = null
    ) : ChatApiService {
        val loginRequests = mutableListOf<ChatLoginRequest>()
        val registerRequests = mutableListOf<ChatRegisterRequest>()

        override suspend fun login(request: ChatLoginRequest): Response<ChatAuthResponse> {
            loginRequests.add(request)
            return loginResult ?: Response.error(401, "".toResponseBody())
        }

        override suspend fun register(request: ChatRegisterRequest): Response<ChatAuthResponse> {
            registerRequests.add(request)
            return registerResult ?: Response.error(400, "".toResponseBody())
        }

        override suspend fun getChats(): Response<List<ChatResponse>> = error("unused")
        override suspend fun getMyProfile(): Response<ChatUserResponse> = error("unused")
        override suspend fun uploadAvatar(file: okhttp3.MultipartBody.Part): Response<com.example.muzea.data.model.AvatarResponse> = error("unused")
        override suspend fun deleteAvatar(): Response<Unit> = error("unused")
        override suspend fun getContacts(): Response<List<ContactResponse>> = error("unused")
        override suspend fun addContact(request: AddContactRequest): Response<ContactResponse> = error("unused")
        override suspend fun getUserByUsername(username: String): Response<ChatUserResponse> = error("unused")
        override suspend fun createPrivateChat(request: CreatePrivateChatRequest): Response<ChatResponse> = error("unused")
        override suspend fun getMessages(
            chatUuid: String,
            page: Int,
            size: Int
        ): Response<PageResponse<MessageResponse>> = error("unused")

        override suspend fun sendMessage(chatUuid: String, request: SendMessageRequest): Response<MessageResponse> =
            error("unused")
    }

    private open class FakeTokenStore : ChatTokenStore {
        var storedUsername: String? = null
        var storedPassword: String? = null
        var storedEmail: String? = null
        var storedChatToken: String? = null
        var storedChatTokenUser: String? = null

        override fun getUsername(): String? = storedUsername
        override fun getPassword(): String? = storedPassword
        override fun getEmail(): String? = storedEmail
        override fun getChatToken(): String? = storedChatToken
        override fun getChatTokenUser(): String? = storedChatTokenUser
        override fun saveChatToken(token: String) {
            storedChatToken = token
        }

        override fun saveChatTokenUser(username: String) {
            storedChatTokenUser = username
        }

        override fun clearChatToken() {
            storedChatToken = null
            storedChatTokenUser = null
        }
    }

    private fun success(token: String = "token-1") = Response.success(
        ChatAuthResponse(
            token = token,
            refreshToken = null,
            userUuid = null,
            username = null,
            email = null,
            avatarUrl = null
        )
    )

    @Test
    fun `isAuthenticated returns true when valid token belongs to current user`() = runTest {
        val store = FakeTokenStore().apply {
            storedUsername = "alice"
            storedChatToken = "existing-token"
            storedChatTokenUser = "alice"
        }
        val manager = ChatAuthManager(FakeApi(), store)

        assertTrue(manager.isAuthenticated())
    }

    @Test
    fun `isAuthenticated clears token when it belongs to another user`() = runTest {
        val store = FakeTokenStore().apply {
            storedUsername = "bob"
            storedChatToken = "token-for-alice"
            storedChatTokenUser = "alice"
            storedPassword = "pass"
            storedEmail = "bob@mail.com"
        }
        val api = FakeApi(loginResult = success())
        val manager = ChatAuthManager(api, store)

        assertTrue(manager.isAuthenticated())

        assertTrue(api.loginRequests.size == 1)
        assertTrue(api.loginRequests[0].username == "bob")
        assertTrue(store.storedChatToken == "token-1")
        assertTrue(store.storedChatTokenUser == "bob")
    }

    @Test
    fun `isAuthenticated falls back to registration when login fails`() = runTest {
        val store = FakeTokenStore().apply {
            storedUsername = "carol"
            storedPassword = "secret"
            storedEmail = "carol@mail.com"
        }
        val api = FakeApi(
            loginResult = Response.error(401, "".toResponseBody()),
            registerResult = success("register-token")
        )
        val manager = ChatAuthManager(api, store)

        assertTrue(manager.isAuthenticated())
        assertTrue(store.storedChatToken == "register-token")
        assertTrue(store.storedChatTokenUser == "carol")
        assertTrue(api.registerRequests.single().username == "carol")
        assertTrue(api.registerRequests.single().email == "carol@mail.com")
    }

    @Test
    fun `isAuthenticated registers with generated email when missing`() = runTest {
        val store = FakeTokenStore().apply {
            storedUsername = "dave"
            storedPassword = "secret"
        }
        val api = FakeApi(loginResult = Response.error(401, "".toResponseBody()), registerResult = success())
        val manager = ChatAuthManager(api, store)

        assertTrue(manager.isAuthenticated())
        assertTrue(api.registerRequests.single().email == "dave@example.com")
    }

    @Test
    fun `isAuthenticated returns false without credentials`() = runTest {
        val store = FakeTokenStore()
        val manager = ChatAuthManager(FakeApi(), store)

        assertFalse(manager.isAuthenticated())
    }

    @Test
    fun `invalidate clears stored chat token`() {
        val store = FakeTokenStore().apply {
            storedChatToken = "x"
            storedChatTokenUser = "y"
        }
        val manager = ChatAuthManager(FakeApi(), store)

        manager.invalidate()

        assertTrue(store.storedChatToken == null)
        assertTrue(store.storedChatTokenUser == null)
    }
}
