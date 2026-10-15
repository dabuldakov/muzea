package com.example.muzea.data.api

import com.example.muzea.data.model.AuthResponse
import com.example.muzea.data.model.LoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Синхронный логин основного API.
 *
 * Нужен интерцептору [TokenRefreshInterceptor]: он выполняется не в корутине, а
 * на сетевом потоке OkHttp, поэтому suspend-версия из [ApiService] здесь не
 * подходит. Клиент для этого интерфейса собирается отдельно и не проходит через
 * интерцепторы авторизации — иначе перелогин зациклился бы сам на себя.
 */
interface AuthApi {

    @POST("/auth/login")
    fun login(@Body request: LoginRequest): Response<AuthResponse>
}
