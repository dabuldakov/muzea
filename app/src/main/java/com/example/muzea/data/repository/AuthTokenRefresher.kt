package com.example.muzea.data.repository

import com.example.muzea.data.api.AuthTokenApi
import com.example.muzea.data.model.AuthServiceRefreshRequest
import com.example.muzea.utils.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обновление access-токена через refresh-токен единого IdP (auth-service).
 *
 * Токен живёт ~15 минут; при 401/403 [com.example.muzea.data.api.TokenRefreshInterceptor]
 * вызывает [refreshToken], тот меняет пару access+refresh (сервер ротирует
 * refresh) и сохраняет новую. Параллельные вызовы защищены мьютексом, чтобы не
 * обновлять токен всем потоком сразу.
 */
@Singleton
class AuthTokenRefresher @Inject constructor(
    private val tokenManager: TokenManager,
    private val authTokenApi: AuthTokenApi
) {

    private val lock = Any()

    fun currentToken(): String? = tokenManager.getToken()

    /** Возвращает новый access-токен или null, если обновить не удалось. */
    fun refreshToken(): String? = synchronized(lock) {
        val refresh = tokenManager.getAuthRefreshToken()?.takeIf { it.isNotEmpty() } ?: return null

        return try {
            val response = authTokenApi.refresh(
                AuthServiceRefreshRequest(refresh, tokenManager.getDeviceId())
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveAuthRefreshToken(body.refreshToken)
                body.accessToken
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
