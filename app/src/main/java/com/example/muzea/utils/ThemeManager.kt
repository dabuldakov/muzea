package com.example.muzea.utils

import android.content.Context
import com.example.muzea.R

/**
 * Цветовая тема приложения: стандартная, violet или pink.
 *
 * Дизайн и расположение элементов не меняются — только акцентный цвет.
 * Выбор хранится в SharedPreferences и применяется в Activity до super.onCreate.
 */
object ThemeManager {

    const val STANDARD = "standard"
    const val VIOLET = "violet"
    const val PINK = "pink"

    private const val PREFS = "ui_prefs"
    private const val KEY = "color_theme"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, STANDARD) ?: STANDARD

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
    }

    fun themeRes(context: Context): Int = when (current(context)) {
        VIOLET -> R.style.Theme_Muzea_Violet
        PINK -> R.style.Theme_Muzea_Pink
        else -> R.style.Theme_Muzea_Standard
    }
}
