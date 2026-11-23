package com.example.muzea.ui.chat

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.example.muzea.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Диалоги действий над сообщением: подтверждения удаления/блокировки и
 * комментарий при пересылке. Вынесены, чтобы фрагмент не собирал их руками.
 */
internal object ChatDialogs {

    fun confirmDeleteMessage(context: Context, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_message_confirm_title)
            .setMessage(R.string.delete_message_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun confirmDeleteSelected(context: Context, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_selected_title)
            .setMessage(R.string.delete_selected_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun confirmBlock(context: Context, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.block_confirm_title)
            .setMessage(R.string.block_confirm_message)
            .setPositiveButton(R.string.action_block) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Комментарий перед пересылкой; тап мимо окна не теряет пересылку. */
    fun forwardComment(context: Context, inflater: LayoutInflater, onSend: (String?) -> Unit) {
        val view = inflater.inflate(R.layout.dialog_forward_comment, null)
        val input = view.findViewById<EditText>(R.id.etForwardComment)
        val dialog = Dialog(context)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCanceledOnTouchOutside(false)
        view.findViewById<View>(R.id.btnForwardCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnForwardSend).setOnClickListener {
            val comment = input.text?.toString()?.trim().orEmpty()
            dialog.dismiss()
            onSend(comment.ifBlank { null })
        }
        dialog.show()
        input.requestFocus()
    }
}
