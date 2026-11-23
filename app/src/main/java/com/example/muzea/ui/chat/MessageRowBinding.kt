package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.domain.model.Message

internal fun bindPower(view: TextView, message: Message) {
    if (message.power > 0) {
        view.text = view.context.getString(R.string.charge_format, message.power)
        view.visibility = View.VISIBLE
    } else {
        view.visibility = View.GONE
    }
}

private fun opaqueTextColor(context: Context): Int =
    if ((context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    ) android.graphics.Color.WHITE else android.graphics.Color.BLACK

internal fun bindReactions(
    container: LinearLayout,
    message: Message,
    onClick: (Message, String) -> Unit
) {
    container.removeAllViews()
    if (message.reactions.isEmpty()) {
        container.visibility = View.GONE
        return
    }
    container.visibility = View.VISIBLE
    val density = container.resources.displayMetrics.density
    message.reactions.forEach { reaction ->
        val chip = TextView(container.context).apply {
            text = if (reaction.count > 1) "${reaction.emoji} ${reaction.count}" else reaction.emoji
            textSize = 18f
            includeFontPadding = false
            alpha = 1f
            // Тема даёт текст с alpha 87 % — из-за этого эмодзи выглядят
            // полупрозрачными. Для глифов нужен полностью непрозрачный цвет.
            setTextColor(opaqueTextColor(container.context))
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            setBackgroundResource(
                if (reaction.mine) R.drawable.bg_reaction_mine
                else R.drawable.bg_reaction
            )
            setOnClickListener { onClick(message, reaction.emoji) }
        }
        (chip.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.marginEnd = (4 * density).toInt()
        } ?: run {
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (4 * density).toInt() }
        }
        container.addView(chip)
    }
}

/**
 * Цитата ответа над пузырём. Оригинал может быть не загружен — тогда
 * показываем нейтральные «Пользователь»/«Сообщение».
 */
internal fun bindReplyQuote(
    container: View,
    nameView: TextView,
    textView: TextView,
    message: Message,
    replyTo: Message?,
    myUserUuid: String?,
    onReplyClick: (String) -> Unit = {}
) {
    val targetUuid = message.replyToMessageUuid
    if (targetUuid.isNullOrBlank() || message.isDeleted) {
        container.visibility = View.GONE
        container.setOnClickListener(null)
        return
    }
    container.visibility = View.VISIBLE
    val context = container.context
    nameView.text = replySenderName(context, replyTo, myUserUuid)
    textView.text = replyPreviewText(context, replyTo)
    container.setOnClickListener { onReplyClick(targetUuid) }
}
