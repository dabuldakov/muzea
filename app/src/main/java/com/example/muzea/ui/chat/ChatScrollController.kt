package com.example.muzea.ui.chat

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Кнопка «вниз» и поведение прокрутки к последнему/непрочитанному.
 * Вынесено из фрагмента, чтобы экран чата не держал ещё и эту логику.
 */
class ChatScrollController(
    private val recyclerView: RecyclerView,
    private val button: View,
    private val messageCount: () -> Int,
    private val unreadAnchor: () -> Int?,
    private val clearUnreadAnchor: () -> Unit,
    private val labelRes: (hasUnread: Boolean) -> Int,
    private val label: (Int) -> String
) {

    fun isAtBottom(): Boolean = !recyclerView.canScrollVertically(1)

    fun setup() {
        button.setOnClickListener { scrollToTarget() }
    }

    /** Кнопка видна, только когда список не в самом низу. */
    fun update() {
        val visible = messageCount() > 0 && !isAtBottom()
        button.visibility = if (visible) View.VISIBLE else View.GONE
        button.contentDescription = label(labelRes(unreadAnchor() != null))
    }

    private fun scrollToTarget() {
        val last = messageCount() - 1
        if (last < 0) return
        val target = (unreadAnchor() ?: last).coerceIn(0, last)
        // Тап по кнопке не касается списка, поэтому начатый флинг сам
        // не останавливается и перебивает прокрутку — гасим его явно.
        recyclerView.stopScroll()
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.scrollToPositionWithOffset(target, 0)
            ?: recyclerView.scrollToPosition(target)
        // Если флинг успел запросить раскладку, повторяем после неё.
        recyclerView.post {
            if (isAtBottom()) return@post
            (recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(target, 0)
                ?: recyclerView.scrollToPosition(target)
        }
        // Первый тап ведёт к непрочитанному, второй — уже в самый низ.
        clearUnreadAnchor()
        update()
    }
}
