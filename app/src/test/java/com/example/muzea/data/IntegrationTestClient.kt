package com.example.muzea.data

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.api.ChatApiService
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Строит реальные Retrofit-сервисы поверх [MockWebServer].
 * Это позволяет проверять полный контракт с сервером: пути, JSON-поля,
 * десериализацию ответов — как в интеграционных тестах, но без эмулятора.
 */
object IntegrationTestClient {

    private fun okHttp() = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    fun mainApi(server: MockWebServer): ApiService = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(okHttp())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    fun chatApi(server: MockWebServer): ChatApiService = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(okHttp())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ChatApiService::class.java)
}