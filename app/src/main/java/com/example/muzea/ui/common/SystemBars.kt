package com.example.muzea.ui.common

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Единая настройка системных панелей для всех экранов.
 *
 * Начиная с Android 15 (targetSdk 35+) приложение обязано рисовать
 * edge-to-edge, поэтому контент уходит под строку состояния. Здесь мы
 * оставляем панели прозрачными, но добавляем отступы системных баров
 * контейнеру активности — контент больше не залезает под часы и иконки.
 *
 * [applyBottomInset] = false нужен там, где нижний отступ уже обрабатывает
 * сам виджет (BottomNavigationView в MainActivity).
 */
object SystemBars {

    fun apply(activity: Activity, applyBottomInset: Boolean = true) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Без серой подложки система не рисует контрастную полосу
            // под навигационной панелью.
            window.isNavigationBarContrastEnforced = false
        }

        val lightTheme = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }

        val content = activity.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Edge-to-edge: adjustResize сам окно не сжимает, поэтому клавиатуру
            // учитываем вручную — иначе поле ввода остаётся за ней.
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(if (applyBottomInset) bars.bottom else 0, ime)
            )
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }
}
