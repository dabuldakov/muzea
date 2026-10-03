package com.example.muzea.utils

import com.example.muzea.data.repository.ChatAuthManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохраняет FCM-токен устройства и отправляет его на chat-бэкенд,
 * чтобы тот мог присылать пуши. Регистрация выполняется после
 * успешной аутентификации в чате.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val tokenManager: TokenManager,
    private val chatAuthManager: ChatAuthManager
) {

    fun sync() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> register(token) }
    }

    fun register(token: String) {
        tokenManager.saveFcmToken(token)
        tokenManager.saveRegisteredFcmToken("")

        CoroutineScope(Dispatchers.IO).launch {
            chatAuthManager.isAuthenticated()
        }
    }
}
