package com.example.muzea.utils

import android.content.Context

/**
 * Согласие пользователя на обработку персональных данных (ст. 9 ФЗ-152).
 * Хранится версия согласия: при смене редакции политики экран согласия
 * показывается заново, даже если пользователь уже принимал предыдущую.
 */
object ConsentManager {

    private const val PREFS = "consent_prefs"
    private const val KEY_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT = "accepted_at"

    /** Текущая редакция согласия. Увеличивайте при изменении текста/политики. */
    const val VERSION = 1

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0) == VERSION

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VERSION, VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun revoke(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
