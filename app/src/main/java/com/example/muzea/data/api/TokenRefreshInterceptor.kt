package com.example.muzea.data.api

import com.example.muzea.data.repository.AuthTokenRefresher
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * При 401/403 от ресурс-сервисов один раз обновляет access-токен через
 * refresh-токен IdP (auth-service) и повторяет запрос.
 *
 * Повторяем ровно один раз, чтобы не зациклиться, и никогда не трогаем
 * эндпоинты auth-service — там 401 означает неверный код/токен, а не протухший
 * access.
 */
class TokenRefreshInterceptor @Inject constructor(
    private val tokenRefresher: AuthTokenRefresher
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401 && response.code != 403) return response

        val path = request.url.encodedPath
        if (path.startsWith(AUTH_PREFIX)) return response
        if (request.header(HEADER_RETRY) != null) return response

        android.util.Log.i("TokenRefreshInterceptor", "got ${response.code} on $path — trying token refresh")

        // Если параллельный запрос уже обновил токен — берём его, не обновляемся снова.
        val requestToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val currentToken = tokenRefresher.currentToken()
        val newToken = if (currentToken != null && currentToken != requestToken) {
            currentToken
        } else {
            tokenRefresher.refreshToken() ?: run {
                android.util.Log.w("TokenRefreshInterceptor", "no new token, returning ${response.code}")
                return response
            }
        }

        response.close()
        val retried = request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header(HEADER_RETRY, "1")
            .build()
        return chain.proceed(retried)
    }

    private companion object {
        const val HEADER_RETRY = "X-Muzea-Retry"
        const val AUTH_PREFIX = "/auth/"
    }
}
