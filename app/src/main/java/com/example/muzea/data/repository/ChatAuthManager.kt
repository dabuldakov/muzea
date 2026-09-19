package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.FcmTokenRequest
import com.example.muzea.utils.ChatTokenStore
import com.example.muzea.utils.FcmTokenStore

/**
 * Отвечает за аутентификацию на чат-сервере: проверка владельца сохранённого
 * токена, повторный логин/регистрация при невалидном или чужом токене.
 * Выделен из ChatRepository, чтобы у последнего осталась одна ответственность —
 * операции с чатами.
 */
class ChatAuthManager(
    private val apiService: ChatApiService,
    private val tokenStore: ChatTokenStore
) {

    private val fcmStore: FcmTokenStore? get() = tokenStore as? FcmTokenStore

    suspend fun isAuthenticated(): Boolean {
        val currentUsername = tokenStore.getUsername()

        if (!tokenStore.getChatToken().isNullOrEmpty()) {
            if (currentUsername != null && tokenStore.getChatTokenUser() != currentUsername) {
                tokenStore.clearChatToken()
            } else {
                registerFcmTokenIfAny()
                return true
            }
        }

        val username = currentUsername
        val password = tokenStore.getPassword()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return false

        val authenticated = tryLogin(username, password) || tryRegister(username, password)
        if (authenticated) registerFcmTokenIfAny()
        return authenticated
    }

    fun invalidate() {
        tokenStore.clearChatToken()
    }

    private suspend fun tryLogin(username: String, password: String): Boolean = try {
        val response = apiService.login(
            ChatLoginRequest(username, password, deviceId(), deviceName(), deviceType())
        )
        if (response.isSuccessful && response.body() != null) {
            tokenStore.saveChatToken(response.body()!!.token)
            tokenStore.saveChatTokenUser(username)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    private suspend fun tryRegister(username: String, password: String): Boolean = try {
        val email = tokenStore.getEmail() ?: "$username@example.com"
        val response = apiService.register(
            ChatRegisterRequest(username, email, password, deviceId(), deviceName(), deviceType())
        )
        if (response.isSuccessful && response.body() != null) {
            tokenStore.saveChatToken(response.body()!!.token)
            tokenStore.saveChatTokenUser(username)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    private suspend fun registerFcmTokenIfAny() {
        val store = fcmStore ?: return
        val token = store.getFcmToken() ?: return
        if (token == store.getRegisteredFcmToken()) return
        try {
            val response = apiService.registerFcmToken(FcmTokenRequest(token, store.getDeviceId()))
            if (response.isSuccessful) store.saveRegisteredFcmToken(token)
        } catch (e: Exception) {
            // Повторим при следующей проверке аутентификации.
        }
    }

    private fun deviceId(): String? = fcmStore?.getDeviceId()
    private fun deviceName(): String? = fcmStore?.getDeviceName()
    private fun deviceType(): String? = fcmStore?.getDeviceType()
}