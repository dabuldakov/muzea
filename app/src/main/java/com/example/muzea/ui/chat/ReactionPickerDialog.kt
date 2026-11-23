package com.example.muzea.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.muzea.R
import com.example.muzea.domain.model.Message
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Быстрый выбор реакции: сетка эмодзи в диалоге. Отдельный файл, чтобы
 * фрагмент не собирал вьюхи руками.
 */
internal object ReactionPickerDialog {

    private val emojis = listOf(
        "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
        "🙏", "💯", "😍", "🤔", "😡", "🥰", "😎", "🤝",
        "✅", "❌", "👀", "🤯", "😴", "💜", "⚡", "🚀"
    )

    fun show(context: Context, message: Message, onPick: (Message, String) -> Unit) {
        val density = context.resources.displayMetrics.density
        val grid = GridLayout(context).apply { columnCount = 6 }
        val size = (48 * density).toInt()
        val textColor = opaqueEmojiTextColor(context)
        var dialog: AlertDialog? = null
        emojis.forEach { emoji ->
            val cell = TextView(context).apply {
                text = emoji
                textSize = 26f
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(textColor)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                }
                setOnClickListener {
                    onPick(message, emoji)
                    dialog?.dismiss()
                }
            }
            grid.addView(cell)
        }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_react)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Тема даёт текст с alpha 87 % — эмодзи из-за этого выглядят бледными. */
    private fun opaqueEmojiTextColor(context: Context): Int =
        if ((context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) Color.WHITE else Color.BLACK
}
