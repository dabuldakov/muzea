package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.muzea.R

/**
 * Плавающая модалка действий над сообщением.
 *
 * Показывается у самого пузыря, не забирает фокус — клавиатура остаётся
 * открытой. Сверху последние 15 реакций в строку, стрелка разворачивает
 * полный набор; ниже — действия иконками.
 */
class MessageActionsPopup(
    private val context: Context,
    private val messageUuid: String,
    private val actions: List<MessageActionSpec>,
    private val onAction: (String) -> Unit,
    private val onReaction: (String) -> Unit
) {

    private val density = context.resources.displayMetrics.density
    private val content: View = LayoutInflater.from(context)
        .inflate(R.layout.popup_message_actions, null, false)
    private val popup = PopupWindow(
        content,
        (300 * density).toInt(),
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 12f * density
    }

    private var expanded = false

    fun show(anchor: View) {
        renderReactions()
        renderActions()
        val width = (300 * density).toInt()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = content.measuredHeight
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val metrics = context.resources.displayMetrics
        val margin = (8 * density).toInt()
        val x = (location[0] + anchor.width / 2 - width / 2)
            .coerceIn(margin, metrics.widthPixels - width - margin)
        var y = location[1] - height - margin
        if (y < margin) y = location[1] + anchor.height + margin
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    fun isShowing(): Boolean = popup.isShowing

    private fun renderReactions() {
        val recent = content.findViewById<LinearLayout>(R.id.recentReactions)
        val full = content.findViewById<GridLayout>(R.id.fullReactions)
        val arrow = content.findViewById<ImageButton>(R.id.btnExpandReactions)
        recent.removeAllViews()
        full.removeAllViews()
        val size = (38 * density).toInt()

        ReactionHistory.recent(context).forEach { emoji ->
            recent.addView(emojiView(emoji, size))
        }
        ReactionHistory.FULL.forEach { emoji ->
            full.addView(emojiView(emoji, size))
        }
        arrow.setOnClickListener {
            expanded = !expanded
            full.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.rotation = if (expanded) 90f else 0f
            dismissOnAnchorOutside()
        }
    }

    private fun emojiView(emoji: String, size: Int): TextView =
        TextView(context).apply {
            text = emoji
            textSize = 22f
            includeFontPadding = false
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                ReactionHistory.record(context, emoji)
                onReaction(emoji)
                dismiss()
            }
        }

    private fun renderActions() {
        val row = content.findViewById<LinearLayout>(R.id.actionsRow)
        row.removeAllViews()
        actions.filter { it.id != MessageActionsBottomSheet.ACTION_REACT }
            .forEach { spec ->
                val button = ImageButton(context).apply {
                    setImageResource(spec.iconRes)
                    contentDescription = context.getString(spec.labelRes)
                    background = null
                    val pad = (9 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        com.google.android.material.color.MaterialColors.getColor(
                            content, com.google.android.material.R.attr.colorOnSurface
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        (44 * density).toInt(), (44 * density).toInt()
                    )
                    setOnClickListener {
                        onAction(spec.id)
                        dismiss()
                    }
                }
                row.addView(button)
            }
        row.visibility = if (row.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun dismissOnAnchorOutside() = Unit
}
