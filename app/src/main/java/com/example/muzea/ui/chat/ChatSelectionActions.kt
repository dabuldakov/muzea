package com.example.muzea.ui.chat

import android.content.Context
import android.widget.Toast
import com.example.muzea.R
import com.example.muzea.domain.model.Message

/**
 * Действия над выбранными сообщениями: удаление своих и пересылка пачки.
 */
internal class ChatSelectionActions(
    private val context: Context,
    private val myUserUuid: () -> String?,
    private val selectedItems: () -> List<Message>,
    private val deleteMessage: (Message) -> Unit,
    private val exitSelection: () -> Unit,
    private val startForward: (List<Message>) -> Unit
) {

    fun requestDelete() {
        val deletable = selectedItems().filter {
            it.messageId != null && it.senderUuid == myUserUuid()
        }
        if (deletable.isEmpty()) {
            Toast.makeText(context, R.string.delete_message_failed, Toast.LENGTH_SHORT).show()
            return
        }
        ChatDialogs.confirmDeleteSelected(context) {
            deletable.forEach(deleteMessage)
            exitSelection()
        }
    }

    fun requestForward() {
        val messages = selectedItems()
        if (messages.isEmpty()) return
        exitSelection()
        startForward(messages)
    }
}
