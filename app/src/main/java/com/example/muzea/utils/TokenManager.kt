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
        migrateLegacyTokens()
    }

    /**
     * Ранее пароль лежал в app_prefs открытым текстом. Переносим его в шифрованное
     * хранилище и удаляем из обычных настроек.
     */
    private fun migrateLegacyPassword() {
        val legacy = prefs.getString("password", null) ?: return
        if (!legacy.isNullOrEmpty()) {
            SecureStore.savePassword(appContext, legacy)
        }
        prefs.edit().remove("password").apply()
    }

    /**
     * Токены раньше тоже лежали в app_prefs открытым текстом. Переносим их в
     * шифрованное хранилище (однократно, при первом запуске после обновления).
     */
    private fun migrateLegacyTokens() {
        prefs.getString("auth_token", null)?.takeIf { it.isNotEmpty() }?.let {
            SecureStore.saveAuthToken(appContext, it)
        }
        prefs.getString("chat_token", null)?.takeIf { it.isNotEmpty() }?.let {
            SecureStore.saveChatToken(appContext, it)
        }
        if (prefs.contains("auth_token") || prefs.contains("chat_token")) {
            prefs.edit().remove("auth_token").remove("chat_token").apply()
        }
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
        SecureStore.saveAuthToken(appContext, token)
    }

    fun getToken(): String? {
        return SecureStore.loadAuthToken(appContext)
    }

    fun saveAuthRefreshToken(token: String) {
        SecureStore.saveAuthRefreshToken(appContext, token)
    }

    fun getAuthRefreshToken(): String? {
        return SecureStore.loadAuthRefreshToken(appContext)
    }

    fun saveChatUserUuid(uuid: String) {
        prefs.edit().putString("chat_user_uuid", uuid).apply()
    }

    fun getChatUserUuid(): String? {
        return prefs.getString("chat_user_uuid", null)
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
        SecureStore.savePassword(appContext, password)
    }

    override fun getPassword(): String? {
        return SecureStore.loadPassword(appContext)
    }

    override fun saveChatToken(token: String) {
        SecureStore.saveChatToken(appContext, token)
    }

    override fun getChatToken(): String? {
        return SecureStore.loadChatToken(appContext)
    }

    override fun saveChatTokenUser(username: String) {
        prefs.edit().putString("chat_token_user", username).apply()
    }

    override fun getChatTokenUser(): String? {
        return prefs.getString("chat_token_user", null)
    }

    override fun clearChatToken() {
        SecureStore.clearChatToken(appContext)
        prefs.edit().remove("chat_token_user").apply()
    }

    fun clearToken() {
        // Заодно чистим legacy-ключи, если миграция ещё не отработала.
        prefs.edit()
            .remove("username").remove("chat_token_user").remove("chat_user_uuid")
            .remove("auth_token").remove("chat_token")
            .apply()
        SecureStore.clearAll(appContext)
    }

    /**
     * Полная очистка локальных данных пользователя — при удалении аккаунта
     * или отзыве согласия на обработку персональных данных.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        SecureStore.clearAll(appContext)
    }
}