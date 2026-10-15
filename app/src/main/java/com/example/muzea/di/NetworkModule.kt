package com.example.muzea.di

import android.content.Context
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.api.AuthApi
import com.example.muzea.data.api.AuthInterceptor
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.api.ChatAuthInterceptor
import com.example.muzea.data.api.GitHubApi
import com.example.muzea.data.api.TokenRefreshInterceptor
import com.example.muzea.utils.Constants
import com.example.muzea.utils.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Сетевой слой: токены, HTTP-клиенты и Retrofit-сервисы основного и чат-API.
 *
 * Раньше каждый экран создавал это сам; теперь это единый граф зависимостей.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    @MainHttpClient
    fun provideMainHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor
    ): OkHttpClient =
        baseBuilder(authInterceptor)
            // После добавления токена: при 401/403 перелогинивается и повторяет.
            .addInterceptor(tokenRefreshInterceptor)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    /**
     * Клиент и Retrofit только для перелогина. Без интерцепторов авторизации,
     * чтобы запрос логина не проходил через перелогин и не зациклился.
     */
    @Provides
    @Singleton
    fun provideAuthApi(): AuthApi =
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)

    @Provides
    @Singleton
    @ChatHttpClient
    fun provideChatHttpClient(chatAuthInterceptor: ChatAuthInterceptor): OkHttpClient =
        baseBuilder(chatAuthInterceptor).build()

    @Provides
    @Singleton
    fun provideApiService(@MainHttpClient client: OkHttpClient): ApiService =
        retrofit(Constants.BASE_URL, client).create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideChatApiService(@ChatHttpClient client: OkHttpClient): ChatApiService =
        retrofit(Constants.CHAT_BASE_URL, client).create(ChatApiService::class.java)

    /**
     * GitHub — публичный API без авторизации: отдельный чистый клиент без
     * интерцепторов основного API, чтобы не отправлять туда наш токен.
     */
    @Provides
    @Singleton
    fun provideGitHubApi(): GitHubApi =
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)

    private const val GITHUB_BASE_URL = "https://api.github.com/"

    private fun baseBuilder(interceptor: Interceptor): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
