package com.example.muzea.data.repository

import com.example.muzea.data.api.AuthApi
import com.example.muzea.data.model.LoginRequest
import com.example.muzea.utils.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обновление токена основного API по сохранённым логину и паролю.
 *
 * Основной JWT у api-muzea истекает, а автообновления у него не было (в отличие
 * от чат-токена). После истечения публичные GET продолжали работать, а любые
 * операции записи отдавали 401/403 — новости и видео «переставали добавляться».
 * [refreshToken] молча логинится заново и сохраняет свежий токен.
 *
 * Перелогин защищён мьютексом: несколько параллельных запросов, получивших 403,
 * не будут логиниться одновременно.
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) {

    private val lock = Any()

    fun currentToken(): String? = tokenManager.getToken()

    /** Возвращает новый токен или null, если перелогиниться не удалось. */
    fun refreshToken(): String? = synchronized(lock) {
        val username = tokenManager.getUsername().orEmpty()
        val password = tokenManager.getPassword().orEmpty()
        if (username.isEmpty() || password.isEmpty()) return null

        return try {
            val response = authApi.login(LoginRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                tokenManager.saveToken(body.token)
                tokenManager.saveUsername(body.username)
                tokenManager.savePassword(password)
                body.token
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
