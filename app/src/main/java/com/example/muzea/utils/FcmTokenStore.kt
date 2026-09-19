package com.example.muzea.utils

/**
 * Устройство и FCM-токен, нужные для пуш-уведомлений.
 * Отдельный интерфейс, чтобы ChatAuthManager мог зарегистрировать токен,
 * не завися от Android-реализации (TokenManager).
 */
interface FcmTokenStore {
    fun getDeviceId(): String?
    fun getDeviceName(): String?
    fun getDeviceType(): String?
    fun getFcmToken(): String?
    fun saveFcmToken(token: String)
    fun getRegisteredFcmToken(): String?
    fun saveRegisteredFcmToken(token: String)
}
