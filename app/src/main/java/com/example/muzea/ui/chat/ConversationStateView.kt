package com.example.muzea.ui.chat

import android.view.View
import android.widget.TextView

/** Состояние экрана чата: загрузка, ошибка и видимость ленты. */
class ConversationStateView(
    private val progress: View,
    private val error: TextView,
    private val list: View
) {

    fun render(isLoading: Boolean, hasMessages: Boolean, errorText: String?) {
        progress.visibility = if (isLoading && !hasMessages) View.VISIBLE else View.GONE

        if (errorText != null && !hasMessages) {
            error.text = errorText
            error.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            error.visibility = View.GONE
            list.visibility = if (hasMessages) View.VISIBLE else View.GONE
        }
    }
}
