package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Хранение пароля от учётной записи в шифрованном виде (ключи — Android Keystore).
 * Открытый текст в обычных SharedPreferences недопустим: файл доступен другим
 * приложениям с правами root/backup и попадает в системные бэкапы.
 * Если хранилище недоступно (например, сломанный Keystore после сброса экрана),
 * пароль не сохраняется вовсе — чат просто потребует повторного входа через приложение.
 */
object SecurePasswordStore {

    private const val FILE_NAME = "secure_prefs"
    private const val KEY_PASSWORD = "account_password"

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
                Log.e("SecurePasswordStore", "Encrypted storage unavailable", e)
                null
            }
        }
    }

    fun save(context: Context, password: String) {
        prefs(context)?.edit()?.putString(KEY_PASSWORD, password)?.apply()
    }

    fun load(context: Context): String? = prefs(context)?.getString(KEY_PASSWORD, null)

    fun clear(context: Context) {
        prefs(context)?.edit()?.remove(KEY_PASSWORD)?.apply()
    }
}
