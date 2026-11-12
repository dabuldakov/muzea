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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.example.muzea.R

/**
 * Плавающая модалка действий над сообщением.
 *
 * Показывается у самого пузыря, не забирает фокус — клавиатура остаётся
 * открытой. Сверху последние реакции в строку со прокруткой, стрелка
 * разворачивает остальные; ниже — действия иконками, под ними статус
 * доставки (свёрнуто — аватарки, по тапу — список).
 */
class MessageActionsPopup(
    private val context: Context,
    private val messageUuid: String,
    private val actions: List<MessageActionSpec>,
    private val statusRows: List<MessageStatusRow> = emptyList(),
    private val showStatus: Boolean = false,
    private val onAction: (String) -> Unit,
    private val onReaction: (String) -> Unit,
    private val onDismissed: () -> Unit = {}
) {

    private val density = context.resources.displayMetrics.density
    private val width = ActionsPopupWidth.calculate(
        // «Реакция» — это верхняя полоса, в ряду действий её нет.
        actionsCount = actions.count { it.id != MessageActionsBottomSheet.ACTION_REACT },
        iconSize = (ICON_DP * density).toInt(),
        padding = (8 * density).toInt(),
        screenWidth = context.resources.displayMetrics.widthPixels,
        margin = (8 * density).toInt()
    )
    private val content: View = LayoutInflater.from(context)
        .inflate(R.layout.popup_message_actions, null, false)
    private val popup = PopupWindow(
        content,
        width,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        // Тапы мимо карточки перехватывает прозрачная накладка фрагмента:
        // окно само их не ловит, иначе тап «протекает» до сообщения.
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 12f * density
        setOnDismissListener { onDismissed() }
    }

    private var expanded = false
    private var statusExpanded = false
    private var anchorView: View? = null
    private var tapX: Int? = null
    private var tapY: Int? = null

    private companion object {
        const val MAX_AVATARS = 5
        const val ICON_DP = 38
        const val STATUS_ROW_DP = 32
        const val STATUS_VISIBLE_ROWS = 4.5f
    }

    fun show(anchor: View, tapX: Int? = null, tapY: Int? = null) {
        anchorView = anchor
        this.tapX = tapX
        this.tapY = tapY
        renderReactions()
        renderActions()
        renderStatus()
        val height = measureHeight()
        val position = positionFor(anchor, height)
        popup.showAtLocation(
            anchor.rootView, Gravity.TOP or Gravity.START, position.x, position.y
        )
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    fun isShowing(): Boolean = popup.isShowing

    private fun measureHeight(): Int {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return content.measuredHeight
    }

    private fun positionFor(anchor: View, height: Int): ActionsPopupPosition.Point {
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val metrics = context.resources.displayMetrics
        val margin = (8 * density).toInt()
        // Показываем у точки нажатия, а не по центру пузыря.
        return ActionsPopupPosition.calculate(
            tapX = tapX ?: (location[0] + anchor.width / 2),
            tapY = tapY ?: location[1],
            popupWidth = width,
            popupHeight = height,
            screenWidth = metrics.widthPixels,
            margin = margin,
            screenHeight = metrics.heightPixels
        )
    }

    /**
     * Раскрытие меняет высоту карточки. Пересчитываем позицию, иначе
     * у сообщения внизу экрана список уезжает за край.
     */
    private fun relayout() {
        val anchor = anchorView ?: return
        if (!popup.isShowing) return
        val height = measureHeight()
        val position = positionFor(anchor, height)
        popup.update(position.x, position.y, width, height)
    }

    private fun renderReactions() {
        val recentRow = content.findViewById<LinearLayout>(R.id.recentReactions)
        val full = content.findViewById<GridLayout>(R.id.fullReactions)
        val arrow = content.findViewById<ImageButton>(R.id.btnExpandReactions)
        recentRow.removeAllViews()
        full.removeAllViews()
        val size = (ICON_DP * density).toInt()

        val recent = ReactionHistory.recent(context)
        recent.forEach { emoji ->
            recentRow.addView(emojiView(emoji, size, grid = false))
        }

        // В раскрытом списке — только те реакции, которых нет в полосе,
        // полоса прокручивается по всей истории, дублировать её незачем.
        val rest = ReactionOrder.rest(recent, ReactionHistory.FULL)
        rest.forEach { emoji ->
            full.addView(emojiView(emoji, size, grid = true))
        }
        arrow.visibility = if (rest.isEmpty()) View.GONE else View.VISIBLE
        arrow.setOnClickListener {
            expanded = !expanded
            full.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.rotation = if (expanded) 90f else 0f
            content.post { relayout() }
        }
    }

    private fun emojiView(emoji: String, size: Int, grid: Boolean): TextView =
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
            layoutParams = if (grid) {
                GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setGravity(Gravity.CENTER)
                }
            } else {
                LinearLayout.LayoutParams(size, size)
            }
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
        readers.removeAllViews()
        rows.removeAllViews()
        readers.visibility = View.VISIBLE
        scroll.visibility = View.GONE
        statusExpanded = false

        val readRows = statusRows.filter { it.status == "READ" }
        // Пока никто не прочитал — секции нет вовсе.
        section.visibility =
            if (showStatus && readRows.isNotEmpty()) View.VISIBLE else View.GONE
        if (section.visibility != View.VISIBLE) return

        // Свёрнутый вид — только аватарки прочитавших внахлёст.
        val avatarSize = (32 * density).toInt()
        val overlap = (-9 * density).toInt()
        readRows.take(MAX_AVATARS).forEach { row ->
            readers.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = overlap
                }
                com.example.muzea.utils.AvatarLoader.load(this, row.avatarUrl)
            })
        }
        if (readRows.size > MAX_AVATARS) {
            readers.addView(TextView(context).apply {
                text = "+${readRows.size - MAX_AVATARS}"
                textSize = 12f
                setPadding((6 * density).toInt(), 0, 0, 0)
            })
        }
        readers.setOnClickListener { setStatusExpanded(true) }

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
                setOnClickListener { setStatusExpanded(false) }
            }
            line.addView(ImageView(context).apply {
                val size = (26 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * density).toInt()
                }
                com.example.muzea.utils.AvatarLoader.load(this, row.avatarUrl)
            })
            line.addView(TextView(context).apply {
                text = row.name
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            line.addView(TextView(context).apply {
                text = listOf(
                    context.getString(label), StatusTime.format(row.at)
                ).filter { it.isNotBlank() }.joinToString(" · ")
                textSize = 12f
            })
            rows.addView(line)
        }
        scroll.setOnClickListener { setStatusExpanded(false) }
        rows.setOnClickListener { setStatusExpanded(false) }
        // Четыре с половиной строки: половина пятой подсказывает прокрутку.
        if (statusRows.size > STATUS_VISIBLE_ROWS.toInt()) {
            val row = (STATUS_ROW_DP * density).toInt()
            scroll.layoutParams = scroll.layoutParams.apply {
                height = (row * STATUS_VISIBLE_ROWS).toInt()
            }
        }
    }

    private fun setStatusExpanded(expand: Boolean) {
        if (statusExpanded == expand) return
        statusExpanded = expand
        val readers = content.findViewById<LinearLayout>(R.id.readersRow)
        val scroll = content.findViewById<ScrollView>(R.id.statusScroll)
        readers.visibility = if (expand) View.GONE else View.VISIBLE
        scroll.visibility = if (expand) View.VISIBLE else View.GONE
        content.post { relayout() }
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
                        (ICON_DP * density).toInt(), (ICON_DP * density).toInt()
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
}
