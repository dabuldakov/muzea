package com.example.muzea.ui.chat

import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.example.muzea.domain.model.Message

/**
 * Режим выделения сообщений: состояние выбора, панель сверху и системная
 * кнопка «назад». Фрагмент передаёт адаптер и действия удаления/пересылки.
 */
class SelectionController(
    private val adapter: MessageAdapter,
    private val selectionBar: View,
    private val composerBar: View,
    private val countView: TextView,
    private val backCallback: OnBackPressedCallback,
    private val messages: () -> List<Message>,
    private val countLabel: (Int) -> String,
    private val onDelete: () -> Unit,
    private val onForward: () -> Unit
) {

    val selected = LinkedHashSet<String>()

    val isActive: Boolean
        get() = selected.isNotEmpty()

    fun setup(deleteButton: View, forwardButton: View, closeButton: View) {
        deleteButton.setOnClickListener { onDelete() }
        forwardButton.setOnClickListener { onForward() }
        closeButton.setOnClickListener { exit() }
        backCallback.isEnabled = false
    }

    fun enter(message: Message) {
        selected.clear()
        selected.add(message.messageUuid)
        adapter.setSelection(selected)
        backCallback.isEnabled = true
        selectionBar.visibility = View.VISIBLE
        composerBar.visibility = View.GONE
        updateCount()
    }

    fun toggle(message: Message) {
        if (!selected.remove(message.messageUuid)) {
            selected.add(message.messageUuid)
        }
        adapter.setSelection(selected)
        if (selected.isEmpty()) {
            exit()
        } else {
            updateCount()
        }
    }

    fun exit() {
        selected.clear()
        adapter.setSelection(emptySet())
        backCallback.isEnabled = false
        selectionBar.visibility = View.GONE
        composerBar.visibility = View.VISIBLE
    }

    fun selectedItems(): List<Message> =
        messages().filter { it.messageUuid in selected }

    private fun updateCount() {
        countView.text = countLabel(selected.size)
    }
}
