package com.example.muzea.ui.navigation

import androidx.fragment.app.Fragment

/**
 * Навигация приложения, спрятанная за интерфейсом.
 *
 * Фрагменты не трогают `FragmentManager` напрямую: они просят [Navigator]
 * открыть переписку/настройки/новость или вернуться назад. Реализация —
 * [com.example.muzea.ui.MainActivity].
 */
interface Navigator {
    fun openConversation(
        chatUuid: String,
        title: String,
        avatarUrl: String?,
        unreadCount: Long
    )

    fun openGroupSettings(chatUuid: String, title: String, avatarUrl: String?)

    fun openNewsDetail(newsId: Long)

    fun back()
}

/** Доступ к навигации из фрагмента (хост-активность реализует [Navigator]). */
val Fragment.navigator: Navigator
    get() = requireActivity() as? Navigator
        ?: error("Host activity must implement Navigator")
