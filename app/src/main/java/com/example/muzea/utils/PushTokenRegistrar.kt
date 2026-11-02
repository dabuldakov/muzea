package com.example.muzea.utils

import com.example.muzea.core.DispatcherProvider
import com.example.muzea.data.repository.ChatAuthManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
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
    private val chatAuthManager: ChatAuthManager,
    private val dispatchers: DispatcherProvider
) {

    fun sync() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> register(token) }
    }

    fun register(token: String) {
        tokenManager.saveFcmToken(token)
        tokenManager.saveRegisteredFcmToken("")

        // Отправка идёт через ChatAuthManager: он сам зарегистрирует токен,
        // когда пользователь авторизован, и повторит позже, если нет.
        CoroutineScope(dispatchers.io).launch {
            chatAuthManager.isAuthenticated()
        }
    }
}
