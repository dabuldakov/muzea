package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.domain.model.Message

/**
 * Панель закреплённого сообщения сверху чата: заголовок, превью, крестик.
 * Отвечает только за отображение и сообщает наружу об откреплении/переходе.
 */
class PinnedBarView(
    private val context: Context,
    private val bar: View,
    private val content: View,
    private val unpin: View,
    private val title: TextView,
    private val preview: TextView,
    private val onUnpin: () -> Unit,
    private val onOpen: (String) -> Unit
) {

    private var current: List<Message> = emptyList()

    fun setup() {
        unpin.setOnClickListener {
            if (current.isNotEmpty()) onUnpin()
        }
        content.setOnClickListener {
            current.firstOrNull()?.let { onOpen(it.messageUuid) }
        }
    }

    fun render(pinned: List<Message>) {
        current = pinned
        if (pinned.isEmpty()) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        title.text = if (pinned.size > 1) {
            context.getString(R.string.pinned_messages_count, pinned.size)
        } else {
            context.getString(R.string.pinned_message_title)
        }
        preview.text = replyPreviewText(context, pinned.first())
    }
}
