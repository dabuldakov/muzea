package com.example.muzea

import androidx.fragment.app.FragmentActivity
import com.example.muzea.ui.navigation.Navigator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Пустая activity для Robolectric-тестов фрагментов.
 *
 * Фрагменты с @AndroidEntryPoint берут Hilt-компонент у activity, поэтому
 * хост тоже должен быть entry point. Реализация [Navigator] — no-op: тесты
 * проверяют вызовы через [lastConversation].
 */
@AndroidEntryPoint
class HiltTestActivity : FragmentActivity(), Navigator {

    data class Conversation(
        val chatUuid: String,
        val title: String,
        val avatarUrl: String?,
        val unreadCount: Long,
        val draftText: String?,
        val highlightMessageUuid: String?
    )

    var lastConversation: Conversation? = null
    var lastNewsId: Long? = null

    override fun openConversation(
        chatUuid: String,
        title: String,
        avatarUrl: String?,
        unreadCount: Long,
        draftText: String?,
        highlightMessageUuid: String?
    ) {
        lastConversation = Conversation(
            chatUuid, title, avatarUrl, unreadCount, draftText, highlightMessageUuid
        )
    }

    override fun openGroupSettings(chatUuid: String, title: String, avatarUrl: String?) = Unit

    override fun openNewsDetail(newsId: Long) {
        lastNewsId = newsId
    }

    override fun back() = Unit
}
