package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Шифрованное хранилище учётных данных: пароль и токены (access/refresh чата).
 * Ключи создаются в Android Keystore и не покидают устройство.
 *
 * Раньше пароль лежал здесь, а токены — открытым текстом в обычных настройках
 * (app_prefs). Токен — это фактически ключ доступа к аккаунту, поэтому он должен
 * храниться так же защищённо, как пароль.
 *
 * Если хранилище недоступно (например, сломанный Keystore после сброса экрана),
 * ничего не сохраняется вовсе — чат просто потребует повторного входа.
 */
object SecureStore {

    private const val FILE_NAME = "secure_prefs"
    private const val KEY_PASSWORD = "account_password"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_AUTH_REFRESH = "auth_refresh_token"
    private const val KEY_CHAT_TOKEN = "chat_token"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { prefs = it }
            } catch (e: Exception) {
                Log.e("SecureStore", "Encrypted storage unavailable", e)
                null
            }
        }
    }

    fun savePassword(context: Context, password: String) =
        prefs(context)?.edit()?.putString(KEY_PASSWORD, password)?.apply()

    fun loadPassword(context: Context): String? = prefs(context)?.getString(KEY_PASSWORD, null)

    fun saveAuthToken(context: Context, token: String) =
        prefs(context)?.edit()?.putString(KEY_AUTH_TOKEN, token)?.apply()

    fun loadAuthToken(context: Context): String? = prefs(context)?.getString(KEY_AUTH_TOKEN, null)

    fun saveAuthRefreshToken(context: Context, token: String) =
        prefs(context)?.edit()?.putString(KEY_AUTH_REFRESH, token)?.apply()

    fun loadAuthRefreshToken(context: Context): String? =
        prefs(context)?.getString(KEY_AUTH_REFRESH, null)

    fun saveChatToken(context: Context, token: String) =
        prefs(context)?.edit()?.putString(KEY_CHAT_TOKEN, token)?.apply()

    fun loadChatToken(context: Context): String? = prefs(context)?.getString(KEY_CHAT_TOKEN, null)

    fun clearAuthToken(context: Context) =
        prefs(context)?.edit()?.remove(KEY_AUTH_TOKEN)?.apply()

    fun clearChatToken(context: Context) =
        prefs(context)?.edit()?.remove(KEY_CHAT_TOKEN)?.apply()

    /** Полная очистка учётных данных (удаление аккаунта, отзыв согласия, логаут). */
    fun clearAll(context: Context) {
        prefs(context)?.edit()?.clear()?.apply()
    }
}
