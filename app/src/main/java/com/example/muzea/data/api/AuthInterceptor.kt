package com.example.muzea.data.api

import com.example.muzea.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Добавляет access-токен IdP к запросам, но только к нашим доменам.
 *
 * Клиенты основного/чат-API и так смотрят только на muzea-хосты, однако
 * host-allowlist — страховка от случайной отправки токена на сторонний сервис
 * (например, если клиент переиспользуют с другим baseUrl).
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    /** Видно тестам: позволяет подменить список доверенных хостов. */
    internal var allowedHosts: Set<String> = DEFAULT_HOSTS

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getToken()

        val request = if (!token.isNullOrEmpty() && originalRequest.url.host in allowedHosts) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }

    internal companion object {
        val DEFAULT_HOSTS = setOf("api-muzea.su", "chat-muzea.su", "auth.muzea.su")
    }
}
