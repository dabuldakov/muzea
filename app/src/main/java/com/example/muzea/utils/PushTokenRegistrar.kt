package com.example.muzea.utils

import android.content.Context
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.repository.ChatAuthManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Сохраняет FCM-токен устройства и отправляет его на chat-бэкенд,
 * чтобы тот мог присылать пуши. Регистрация выполняется после
 * успешной аутентификации в чате.
 */
object PushTokenRegistrar {

    fun sync(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> register(context, token) }
    }

    fun register(context: Context, token: String) {
        val tokenManager = TokenManager(context.applicationContext)
        tokenManager.saveFcmToken(token)
        tokenManager.saveRegisteredFcmToken("")

        CoroutineScope(Dispatchers.IO).launch {
            val apiService = ChatRetrofitClient(tokenManager).apiService
            ChatAuthManager(apiService, tokenManager).isAuthenticated()
        }
    }
}
