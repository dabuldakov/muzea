package com.example.muzea.data.api

import com.example.muzea.data.model.AuthServiceRefreshRequest
import com.example.muzea.data.model.AuthServiceTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Синхронное обновление токена для [TokenRefreshInterceptor]: интерцептор
 * работает на сетевом потоке OkHttp, поэтому suspend-версия не подходит.
 * Клиент собирается без интерцепторов авторизации, чтобы не зациклиться.
 */
interface AuthTokenApi {

    @POST("/auth/token/refresh")
    fun refresh(@Body body: AuthServiceRefreshRequest): Response<AuthServiceTokenResponse>
}
