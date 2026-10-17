package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.FcmTokenRequest
import com.example.muzea.utils.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Готовность к работе с чатом на едином токене IdP.
 *
 * Раньше здесь был отдельный логин/регистрация на chat-сервере по паролю
 * (учётные данные дублировали makeup). Теперь access-токен один — от
 * auth-service, и chat-сервер принимает его по JWKS. Здесь остаётся только
 * проверка наличия токена и однократное получение локального chat-профиля
 * (нужен userUuid для API чатов).
 */
@Singleton
class ChatAuthManager @Inject constructor(
    private val apiService: ChatApiService,
    private val tokenManager: TokenManager
) {

    var lastFailureMessage: String? = null
        private set

    suspend fun isAuthenticated(): Boolean {
        lastFailureMessage = null
        if (tokenManager.getToken().isNullOrEmpty()) {
            lastFailureMessage = "Вы не авторизованы. Войдите в приложение."
            return false
        }
        ensureChatProfile()
        registerFcmTokenIfAny()
        return true
    }

    /** Токен единый; 401 обрабатывает refresh-интерсептор, локально чистить нечего. */
    fun invalidate() {
        // no-op
    }

    /**
     * Однократно тянет профиль на chat-сервере: это заодно провижинит локального
     * пользователя и даёт его userUuid (нужен для чатов/контактов).
     */
    private suspend fun ensureChatProfile() {
        if (!tokenManager.getChatUserUuid().isNullOrEmpty()) return
        try {
            val response = apiService.getMyProfile()
            val uuid = response.body()?.userUuid
            if (response.isSuccessful && !uuid.isNullOrEmpty()) {
                tokenManager.saveChatUserUuid(uuid)
            }
        } catch (e: Exception) {
            // Попробуем при следующем обращении.
        }
    }

    private suspend fun registerFcmTokenIfAny() {
        val token = tokenManager.getFcmToken() ?: return
        if (token == tokenManager.getRegisteredFcmToken()) return
        try {
            val response = apiService.registerFcmToken(
                FcmTokenRequest(token, tokenManager.getDeviceId())
            )
            if (response.isSuccessful) tokenManager.saveRegisteredFcmToken(token)
        } catch (e: Exception) {
            // Повторим при следующей проверке аутентификации.
        }
    }
}
