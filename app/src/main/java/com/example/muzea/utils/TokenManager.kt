package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences

class TokenManager(
    context: Context
) {
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

    fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun saveEmail(email: String) {
        prefs.edit().putString("email", email).apply()
    }

    fun getEmail(): String? {
        return prefs.getString("email", null)
    }

    fun savePassword(password: String) {
        prefs.edit().putString("password", password).apply()
    }

    fun getPassword(): String? {
        return prefs.getString("password", null)
    }

    fun saveChatToken(token: String) {
        prefs.edit().putString("chat_token", token).apply()
    }

    fun getChatToken(): String? {
        return prefs.getString("chat_token", null)
    }

    fun clearChatToken() {
        prefs.edit().remove("chat_token").apply()
    }

    fun clearToken() {
        prefs.edit().remove("auth_token").remove("username").remove("chat_token").apply()
    }
}