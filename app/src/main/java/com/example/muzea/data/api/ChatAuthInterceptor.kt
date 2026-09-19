package com.example.muzea.data.api

import com.example.muzea.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class ChatAuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val chatToken = tokenManager.getChatToken()

        val request = if (!chatToken.isNullOrEmpty()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $chatToken")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}