package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.utils.ChatTokenStore

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

    suspend fun isAuthenticated(): Boolean {
        val currentUsername = tokenStore.getUsername()

        if (!tokenStore.getChatToken().isNullOrEmpty()) {
            if (currentUsername != null && tokenStore.getChatTokenUser() != currentUsername) {
                tokenStore.clearChatToken()
            } else {
                return true
            }
        }

        val username = currentUsername
        val password = tokenStore.getPassword()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return false

        return tryLogin(username, password) || tryRegister(username, password)
    }

    fun invalidate() {
        tokenStore.clearChatToken()
    }

    private suspend fun tryLogin(username: String, password: String): Boolean = try {
        val response = apiService.login(ChatLoginRequest(username, password))
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
        val response = apiService.register(ChatRegisterRequest(username, email, password))
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
}