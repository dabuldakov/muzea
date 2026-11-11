package com.example.muzea.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Выбранная тема приложения.
 *
 * По умолчанию — тёмная (основной стиль Muzea). Выбор хранится в
 * SharedPreferences и применяется через AppCompatDelegate, поэтому системная
 * тема на него не влияет.
 */
object ThemePrefs {

    private const val PREFS = "ui_prefs"
    private const val KEY_DARK = "dark_theme"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true)

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
