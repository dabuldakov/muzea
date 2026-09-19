package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences

class TokenManager(
    context: Context
) : ChatTokenStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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
        prefs.edit().putString("password", password).apply()
    }

    override fun getPassword(): String? {
        return prefs.getString("password", null)
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
    }
}