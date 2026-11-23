package com.example.muzea.ui.chat

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow

/**
 * Подсказка участников при вводе `@`: список над полем ввода и подстановка
 * выбранного имени. Живёт вместе с View: создаётся в onViewCreated.
 */
internal class MentionPopupController(
    private val context: Context,
    private val input: EditText
) {

    private var popup: ListPopupWindow? = null
    private var start = -1
    private var end = -1

    fun show(query: MentionSuggestions.Query) {
        start = query.start
        end = query.end
        val popup = popup ?: ListPopupWindow(context).apply {
            anchorView = input
            width = (240 * context.resources.displayMetrics.density).toInt()
            isModal = false
            inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnItemClickListener { _, _, position, _ ->
                val name = listView?.adapter?.getItem(position) as? String
                    ?: return@setOnItemClickListener
                insert(name)
            }
            this@MentionPopupController.popup = this
        }
        popup.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, query.candidates)
        )
        if (!popup.isShowing) popup.show()
    }

    fun dismiss() {
        popup?.dismiss()
        start = -1
        end = -1
    }

    private fun insert(username: String) {
        if (start < 0 || end < start) return
        val text = input.text?.toString().orEmpty()
        val insertion = "@$username "
        val updated = text.substring(0, start) + insertion +
            text.substring(end.coerceAtMost(text.length))
        input.setText(updated)
        val position = (start + insertion.length).coerceAtMost(updated.length)
        input.setSelection(position)
        dismiss()
    }
}
