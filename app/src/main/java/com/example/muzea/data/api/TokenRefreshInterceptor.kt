package com.example.muzea.data.api

import com.example.muzea.data.repository.SessionRefresher
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * При 401/403 от основного API один раз перелогинивается и повторяет запрос.
 *
 * Сервер api-muzea на истёкший токен отвечает 403 (тело пустое), поэтому
 * распознать истечение иначе, чем «отказ на запрос, требующий авторизации»,
 * нельзя. Повторяем ровно один раз, чтобы не зациклиться, и никогда не трогаем
 * сами эндпоинты логина/регистрации — там 401 означает неверный пароль, а не
 * протухший токен.
 */
class TokenRefreshInterceptor @Inject constructor(
    private val sessionRefresher: SessionRefresher
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401 && response.code != 403) return response

        val path = request.url.encodedPath
        if (path.endsWith(LOGIN_PATH) || path.endsWith(REGISTER_PATH)) return response
        if (request.header(HEADER_RETRY) != null) return response

        // Если другой запрос уже обновил токен, просто берём его, а не логинимся
        // повторно всем потоком.
        val requestToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val currentToken = sessionRefresher.currentToken()
        val newToken = if (currentToken != null && currentToken != requestToken) {
            currentToken
        } else {
            sessionRefresher.refreshToken() ?: return response
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
        const val LOGIN_PATH = "/auth/login"
        const val REGISTER_PATH = "/auth/register"
    }
}
