package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.utils.ActiveChatTracker
import com.example.muzea.utils.ChatNotifications

/**
 * Присутствие в чате: активный чат, снятие push, пауза опроса и черновик.
 * Вызывается из lifecycle-хуков фрагмента.
 */
internal class ChatPresenceController(
    private val chatUuid: String,
    private val context: Context,
    private val resumePolling: () -> Unit,
    private val pausePolling: () -> Unit,
    private val saveDraft: (String) -> Unit
) {

    fun onStart(hidden: Boolean) {
        resumePolling()
        // Внутри onStart lifecycle ещё CREATED, поэтому смотрим только на hidden.
        if (!hidden) markVisible()
    }

    fun onStop() {
        clearActive()
        pausePolling()
    }

    /**
     * Вкладки и экраны «поверх» не останавливают фрагмент, а прячут его.
     * Без этого чат считался бы открытым на другой вкладке и push подавлялся.
     */
    fun onHiddenChanged(hidden: Boolean, started: Boolean) {
        if (hidden) {
            clearActive()
        } else if (started) {
            markVisible()
        }
    }

    /** Черновик сохраняем только для обычного ввода, не для режима правки. */
    fun onPause(draft: String?) {
        if (draft != null) saveDraft(draft)
    }

    private fun markVisible() {
        ActiveChatTracker.chatUuid = chatUuid
        ChatNotifications.cancel(context.applicationContext, chatUuid)
    }

    private fun clearActive() {
        if (ActiveChatTracker.chatUuid == chatUuid) ActiveChatTracker.chatUuid = null
    }
}
