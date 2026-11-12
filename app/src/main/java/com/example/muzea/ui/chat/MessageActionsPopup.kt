package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
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

    private companion object {
        const val MAX_AVATARS = 5
    }

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
        val recentRow = content.findViewById<LinearLayout>(R.id.recentReactions)
        val full = content.findViewById<GridLayout>(R.id.fullReactions)
        val arrow = content.findViewById<ImageButton>(R.id.btnExpandReactions)
        recentRow.removeAllViews()
        full.removeAllViews()
        val size = (38 * density).toInt()

        ReactionHistory.recent(context).forEach { emoji ->
            recentRow.addView(emojiView(emoji, size))
        }
        val recent = ReactionHistory.recent(context)
        val ordered = ReactionOrder.ordered(recent, ReactionHistory.FULL)
        ordered.forEach { emoji ->
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
        val readers = content.findViewById<LinearLayout>(R.id.readersRow)
        val scroll = content.findViewById<ScrollView>(R.id.statusScroll)
        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        section.visibility = if (showStatus && statusRows.isNotEmpty()) View.VISIBLE else View.GONE
        readers.removeAllViews()
        rows.removeAllViews()
        scroll.visibility = View.GONE

        val readRows = statusRows.filter { it.status == "READ" }
        if (readRows.isEmpty()) {
            readers.addView(TextView(context).apply {
                text = context.getString(R.string.message_status_none_read)
                textSize = 12f
                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        content, com.google.android.material.R.attr.colorOnSurface
                    )
                )
            })
            return
        }

        // Свёрнутый вид — только аватарки внахлёст, как было в старой шторке.
        val avatarSize = (32 * density).toInt()
        val overlap = (-9 * density).toInt()
        readRows.take(MAX_AVATARS).forEach { row ->
            val avatar = android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = overlap
                }
                com.example.muzea.utils.AvatarLoader.load(this, row.avatarUrl)
            }
            readers.addView(avatar)
        }
        if (readRows.size > MAX_AVATARS) {
            readers.addView(TextView(context).apply {
                text = "+${readRows.size - MAX_AVATARS}"
                textSize = 12f
                setPadding((6 * density).toInt(), 0, 0, 0)
            })
        }
        readers.setOnClickListener {
            val expand = scroll.visibility != View.VISIBLE
            scroll.visibility = if (expand) View.VISIBLE else View.GONE
        }

        // Развёрнутый список: аватарка, имя, статус и время.
        statusRows.forEach { row ->
            val label = when (row.status) {
                "READ" -> R.string.message_status_read
                "DELIVERED" -> R.string.message_status_delivered
                else -> R.string.message_status_sent
            }
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (6 * density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            line.addView(android.widget.ImageView(context).apply {
                val size = (26 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * density).toInt()
                }
                com.example.muzea.utils.AvatarLoader.load(this, row.avatarUrl)
            })
            line.addView(TextView(context).apply {
                text = row.name
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            line.addView(TextView(context).apply {
                text = listOf(
                    context.getString(label), StatusTime.format(row.at)
                ).filter { it.isNotBlank() }.joinToString(" · ")
                textSize = 12f
            })
            rows.addView(line)
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
