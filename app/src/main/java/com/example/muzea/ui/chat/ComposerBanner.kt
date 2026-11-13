package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.domain.model.Message

/**
 * Баннер над полем ввода: ответ на сообщение или режим редактирования.
 * Отвечает только за состояние баннера, отправкой занимается фрагмент.
 */
class ComposerBanner(
    private val context: Context,
    private val banner: View,
    private val cancelButton: ImageButton,
    private val titleView: TextView,
    private val previewView: TextView,
    private val input: EditText,
    private val myUserUuid: () -> String?,
    private val requestInput: () -> Unit
) {

    private var lastReplyUuid: String? = null
    private var lastEditingUuid: String? = null

    fun render(replyTo: Message?, editing: Message?) {
        if (editing != null) {
            banner.visibility = View.VISIBLE
            cancelButton.contentDescription = context.getString(R.string.action_cancel_edit)
            titleView.text = context.getString(R.string.edit_message_title)
            previewView.text = replyPreviewText(context, editing)
            if (editing.messageUuid != lastEditingUuid) {
                lastEditingUuid = editing.messageUuid
                val text = editing.text.orEmpty()
                input.setText(text)
                input.setSelection(text.length)
                input.requestFocus()
                requestInput()
            }
            return
        }
        lastEditingUuid = null

        cancelButton.contentDescription = context.getString(R.string.action_cancel_reply)
        if (replyTo == null) {
            banner.visibility = View.GONE
            lastReplyUuid = null
            return
        }
        banner.visibility = View.VISIBLE
        titleView.text = context.getString(
            R.string.reply_to_format,
            replySenderName(context, replyTo, myUserUuid())
        )
        previewView.text = replyPreviewText(context, replyTo)
        if (replyTo.messageUuid != lastReplyUuid) {
            lastReplyUuid = replyTo.messageUuid
            input.requestFocus()
            requestInput()
        }
    }
}
