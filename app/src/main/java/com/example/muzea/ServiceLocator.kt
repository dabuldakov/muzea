package com.example.muzea

import android.content.Context
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.*
import com.example.muzea.utils.TokenManager

object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val tokenManager by lazy {
        TokenManager(appContext)
    }

    private val apiService by lazy {
        RetrofitClient(tokenManager).apiService
    }

    val authRepository by lazy {
        AuthRepository(apiService, tokenManager)
    }

    val newsRepository by lazy {
        NewsRepository(apiService)
    }

    val videoRepository by lazy {
        VideoRepository(apiService)
    }

    val userRepository by lazy {
        UserRepository(apiService)
    }
}