package com.example.muzea.ui.chat

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.domain.model.Message

/**
 * Прокрутка ленты: якорь при догрузке истории, позиция после DiffUtil и
 * отметка видимых сообщений прочитанными.
 */
internal class ChatMessageListScroller(
    private val recyclerView: RecyclerView,
    private val adapter: () -> MessageAdapter?,
    private val messages: () -> List<Message>,
    private val isViewAlive: () -> Boolean,
    private val onLoadOlder: () -> Unit,
    private val onMarkRead: (String) -> Unit
) {

    private var anchorUuid: String? = null
    private var anchorOffset = 0

    /** Запоминает якорь и просит ViewModel догрузить историю. */
    fun requestOlder() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        val uuid = messages().getOrNull(position)?.messageUuid
        if (uuid != null) {
            anchorUuid = uuid
            anchorOffset = layoutManager.findViewByPosition(position)?.top ?: 0
        }
        onLoadOlder()
    }

    /** Возвращает список к тому же сообщению, что было сверху до догрузки. */
    fun restore() {
        val uuid = anchorUuid ?: return
        anchorUuid = null
        recyclerView.post {
            if (!isViewAlive()) return@post
            val current = adapter() ?: return@post
            val position = current.currentList.indexOfFirst { it.messageUuid == uuid }
            if (position >= 0) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(position, anchorOffset)
            }
        }
    }

    fun scrollTo(position: Int) {
        recyclerView.scrollToPosition(position)
        markVisibleAsRead()
    }

    /**
     * Ставит сообщение к верхнему краю ленты. При stackFromEnd обычный
     * scrollToPosition прижимает его к низу, поэтому выравниваем по пикселям
     * после раскладки.
     */
    fun scrollToTop(position: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        layoutManager.scrollToPositionWithOffset(position, 0)
        recyclerView.post {
            if (!isViewAlive()) return@post
            val view = layoutManager.findViewByPosition(position)
            if (view == null) return@post
            val delta = view.top - recyclerView.paddingTop
            if (delta != 0) recyclerView.scrollBy(0, delta)
            markVisibleAsRead()
        }
    }

    /** Отмечает прочитанным всё до последнего видимого сообщения. */
    fun markVisibleAsRead() {
        val current = adapter() ?: return
        if (!isViewAlive()) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val last = layoutManager.findLastVisibleItemPosition()
        if (last < 0) return
        val message = current.currentList.getOrNull(last) ?: return
        onMarkRead(message.messageUuid)
    }
}
