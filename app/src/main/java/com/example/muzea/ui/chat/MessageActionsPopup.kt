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
    private val statusRows: List<MessageStatusRow> = emptyList(),
    private val showStatus: Boolean = false,
    private val onAction: (String) -> Unit,
    private val onReaction: (String) -> Unit
) {

    private val density = context.resources.displayMetrics.density
    private val actionsWidth = ActionsPopupWidth.calculate(
        actionsCount = actions.size,
        iconSize = (38 * density).toInt(),
        padding = (8 * density).toInt(),
        screenWidth = context.resources.displayMetrics.widthPixels,
        margin = (8 * density).toInt()
    )
    private val content: View = LayoutInflater.from(context)
        .inflate(R.layout.popup_message_actions, null, false)
    private val popup = PopupWindow(
        content,
        actionsWidth,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 12f * density
    }

    private var expanded = false

    fun show(anchor: View, tapX: Int? = null, tapY: Int? = null) {
        renderReactions()
        renderActions()
        renderStatus()
        val width = actionsWidth
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = content.measuredHeight
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val metrics = context.resources.displayMetrics
        val margin = (8 * density).toInt()
        // Показываем у точки нажатия, а не по центру пузыря.
        val centerX = tapX ?: (location[0] + anchor.width / 2)
        val fingerY = tapY ?: location[1]
        val position = ActionsPopupPosition.calculate(
            tapX = centerX,
            tapY = fingerY,
            popupWidth = width,
            popupHeight = height,
            screenWidth = metrics.widthPixels,
            margin = margin
        )
        popup.showAtLocation(
            anchor.rootView, Gravity.TOP or Gravity.START, position.x, position.y
        )
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
            setTextColor(
                if ((context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                ) Color.WHITE else Color.BLACK
            )
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                ReactionHistory.record(context, emoji)
                onReaction(emoji)
                dismiss()
            }
        }

    private fun renderStatus() {
        val section = content.findViewById<LinearLayout>(R.id.statusSection)
        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        section.visibility = if (showStatus && statusRows.isNotEmpty()) View.VISIBLE else View.GONE
        rows.removeAllViews()
        statusRows.forEach { row ->
            val label = when (row.status) {
                "READ" -> R.string.message_status_read
                "DELIVERED" -> R.string.message_status_delivered
                else -> R.string.message_status_sent
            }
            val time = row.at?.substringAfter('T')?.take(5).orEmpty()
            val text = TextView(context).apply {
                text = listOf(row.name, context.getString(label), time)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                textSize = 13f
                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        content, com.google.android.material.R.attr.colorOnSurface
                    )
                )
                val pad = (6 * density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            rows.addView(text)
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
                        (38 * density).toInt(), (38 * density).toInt()
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
