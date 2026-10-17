package com.example.muzea.di

import android.content.Context
import com.example.muzea.BuildConfig
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.api.AuthInterceptor
import com.example.muzea.data.api.AuthServiceApi
import com.example.muzea.data.api.AuthTokenApi
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.api.GitHubApi
import com.example.muzea.data.api.KlipyApi
import com.example.muzea.data.api.TokenRefreshInterceptor
import com.example.muzea.utils.Constants
import com.example.muzea.utils.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
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

    // Публичные ключи выпускающей цепочки серверов (SPKI SHA-256).
    // Проверены на живых api-muzea.su и chat-muzea.su.
    private const val PIN_INTERMEDIATE_CA =
        "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4="
    private const val PIN_ROOT_CA =
        "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w="

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    /** Постоянный кэш гифок в filesDir (переживает перезапуск приложения). */
    @Provides
    @Singleton
    fun provideGifCache(@ApplicationContext context: Context): com.example.muzea.data.repository.GifCache =
        com.example.muzea.data.repository.GifCache(java.io.File(context.filesDir, "gif_cache.json"))

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
     * Клиент для auth-service: без интерцепторов авторизации и без пиннинга CA
     * (это внешний домен IdP; TLS обычный). Отдельные сервисы — suspend для
     * приложения и sync для refresh-интерсептора.
     */
    private fun authRetrofit(vararg interceptors: Interceptor): Retrofit {
        val builder = OkHttpClient.Builder()
            .certificatePinner(certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        interceptors.forEach { builder.addInterceptor(it) }
        return Retrofit.Builder()
            .baseUrl(Constants.AUTH_BASE_URL)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthServiceApi(authInterceptor: AuthInterceptor): AuthServiceApi =
        // AuthInterceptor подставляет access-токен, если он есть: нужен для /auth/me.
        authRetrofit(authInterceptor).create(AuthServiceApi::class.java)

    @Provides
    @Singleton
    fun provideAuthTokenApi(): AuthTokenApi =
        authRetrofit().create(AuthTokenApi::class.java)

    @Provides
    @Singleton
    @ChatHttpClient
    fun provideChatHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor
    ): OkHttpClient =
        baseBuilder(authInterceptor)
            // Единый access-токен IdP; при 401 — обновление пары и повтор.
            .addInterceptor(tokenRefreshInterceptor)
            .build()

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

    /**
     * Klipy — публичный GIF-API. Запросы идут с устройства напрямую (требование
     * провайдера), ключ встроен в сборку. Это внешний хост, поэтому клиент без
     * интерцепторов нашего API и без пиннинга.
     */
    @Provides
    @Singleton
    fun provideKlipyApi(): KlipyApi =
        Retrofit.Builder()
            .baseUrl("https://api.klipy.com/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = if (BuildConfig.DEBUG) {
                                HttpLoggingInterceptor.Level.BODY
                            } else {
                                HttpLoggingInterceptor.Level.NONE
                            }
                        }
                    )
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KlipyApi::class.java)

    private fun baseBuilder(interceptor: Interceptor): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BODY — только в отладочной сборке: в релизе он записывал бы
                    // в logcat тексты сообщений, контент и токены из ответов.
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
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

    /**
     * Пиннинг публичных ключей наших серверов. Пиним промежуточный CA и его
     * издателя (а не лист): это переживает регулярную смену листового
     * сертификата. Если выпускающий CA когда-нибудь сменится — пины нужно
     * обновить, иначе запросы начнут падать (SSLPeerUnverifiedException).
     */
    private fun certificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("api-muzea.su", PIN_INTERMEDIATE_CA, PIN_ROOT_CA)
            .add("chat-muzea.su", PIN_INTERMEDIATE_CA, PIN_ROOT_CA)
            .add("auth.muzea.su", PIN_INTERMEDIATE_CA, PIN_ROOT_CA)
            .build()
}
