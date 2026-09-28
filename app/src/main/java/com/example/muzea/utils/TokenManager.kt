package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class TokenManager(
    context: Context
) : ChatTokenStore, FcmTokenStore {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    init {
        migrateLegacyPassword()
    }

    /**
     * Ранее пароль лежал в app_prefs открытым текстом. Переносим его в шифрованное
     * хранилище и удаляем из обычных настроек.
     */
    private fun migrateLegacyPassword() {
        val legacy = prefs.getString("password", null) ?: return
        if (!legacy.isNullOrEmpty()) {
            SecurePasswordStore.save(appContext, legacy)
        }
        prefs.edit().remove("password").apply()
    }

    override fun getDeviceId(): String {
        prefs.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    override fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override fun getDeviceType(): String = "ANDROID"

    override fun getFcmToken(): String? = prefs.getString("fcm_token", null)

    override fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun getRegisteredFcmToken(): String? = prefs.getString("fcm_token_registered", null)

    override fun saveRegisteredFcmToken(token: String) {
        prefs.edit().putString("fcm_token_registered", token).apply()
    }

    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun saveUsername(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    override fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun saveEmail(email: String) {
        prefs.edit().putString("email", email).apply()
    }

    override fun getEmail(): String? {
        return prefs.getString("email", null)
    }

    fun savePassword(password: String) {
        SecurePasswordStore.save(appContext, password)
    }

    override fun getPassword(): String? {
        return SecurePasswordStore.load(appContext)
    }

    override fun saveChatToken(token: String) {
        prefs.edit().putString("chat_token", token).apply()
    }

    override fun getChatToken(): String? {
        return prefs.getString("chat_token", null)
    }

    override fun saveChatTokenUser(username: String) {
        prefs.edit().putString("chat_token_user", username).apply()
    }

    override fun getChatTokenUser(): String? {
        return prefs.getString("chat_token_user", null)
    }

    override fun clearChatToken() {
        prefs.edit().remove("chat_token").remove("chat_token_user").apply()
    }

    fun clearToken() {
        prefs.edit().remove("auth_token").remove("username").remove("chat_token").remove("chat_token_user").apply()
        SecurePasswordStore.clear(appContext)
    }

    /**
     * Полная очистка локальных данных пользователя — при удалении аккаунта
     * или отзыве согласия на обработку персональных данных.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        SecurePasswordStore.clear(appContext)
    }
}