package com.example.muzea.data.api

import com.example.muzea.data.model.AuthServiceRefreshRequest
import com.example.muzea.data.model.AuthServiceTokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Синхронное обновление токена для [TokenRefreshInterceptor]: интерцептор
 * работает на сетевом потоке OkHttp, поэтому suspend-версия не подходит.
 * Возвращаем [Call], а не [retrofit2.Response]: Retrofit умеет адаптировать
 * `Response<T>` для suspend-функций, но не для синхронных (иначе
 * "Unable to create call adapter"). Клиент собирается без интерцепторов
 * авторизации, чтобы не зациклиться.
 */
interface AuthTokenApi {

    @POST("/auth/token/refresh")
    fun refresh(@Body body: AuthServiceRefreshRequest): Call<AuthServiceTokenResponse>
}
