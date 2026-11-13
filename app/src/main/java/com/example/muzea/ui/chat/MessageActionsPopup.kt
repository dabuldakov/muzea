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
    private val reactions: List<com.example.muzea.domain.model.MessageReaction> = emptyList(),
    /** Авторы реакций для чужих сообщений, где нет списка получателей. */
    private val reactionRows: List<MessageStatusRow> = emptyList(),
    private val showStatus: Boolean = false,
    private val onAction: (String) -> Unit,
    private val onReaction: (String) -> Unit,
    private val onDismissed: () -> Unit = {}
) {

    private val density = context.resources.displayMetrics.density
    private val width = ActionsPopupWidth.calculate(
        // «Реакция» — это верхняя полоса, в ряду действий её нет.
        actionsCount = ActionsPopupWidth.visibleActionsCount(
            actions, MessageActionsBottomSheet.ACTION_REACT
        ),
        iconSize = (ICON_DP * density).toInt(),
        padding = (8 * density).toInt(),
        screenWidth = context.resources.displayMetrics.widthPixels,
        margin = (8 * density).toInt()
    )
    private val content: View = LayoutInflater.from(context)
        .inflate(R.layout.popup_message_actions, null, false)
    /**
     * Карточка живёт в прозрачном окне на весь экран. Окно не меняет размеров —
     * при раскрытии анимируется только сама карточка, поэтому нет рывков от
     * пересоздания окна.
     */
    private val root = android.widget.FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isClickable = true
        setOnClickListener { dismiss() }
    }
    private val popup = PopupWindow(
        root,
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        false
    ).apply {
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setOnDismissListener { onDismissed() }
    }

    init {
        content.isClickable = true
        content.elevation = 12f * density
        root.addView(
            content,
            android.widget.FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private var expanded = false
    private var statusExpanded = false
    private var anchorView: View? = null
    private var tapX: Int? = null
    private var tapY: Int? = null
    private var popupX = 0
    private var popupY = 0
    private var popupHeight = 0
    private var growsUp = false
    private var edgeAnchor = 0
    private var windowOffsetX = 0
    private var windowOffsetY = 0
    private var animator: android.animation.ValueAnimator? = null

    private companion object {
        const val ICON_DP = 38
        const val STRIP_LIMIT = 11
        const val STATUS_ROW_DP = 56
        const val RESIZE_DURATION_MS = 260L
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
        popupX = position.x
        popupY = position.y
        popupHeight = height
        val fingerY = tapY ?: position.y + height
        growsUp = PopupResize.growsUp(position.y, height, fingerY)
        edgeAnchor = if (growsUp) position.y + height else position.y
        applyCardGeometry(popupY, popupHeight)
        content.visibility = View.INVISIBLE
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, 0, 0)
        root.post {
            if (!popup.isShowing) return@post
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            windowOffsetX = location[0]
            windowOffsetY = location[1]
            applyCardGeometry(popupY, popupHeight)
            content.visibility = View.VISIBLE
        }
    }

    /**
     * Ставит карточку внутри полноэкранного окна по экранным координатам.
     * Раскладку применяем сразу: иначе кадр рисуется со старой высотой, но
     * новым сдвигом, и нижний край подрагивает.
     */
    private fun applyCardGeometry(screenY: Int, height: Int) {
        val params = content.layoutParams as android.widget.FrameLayout.LayoutParams
        if (params.height != height) {
            params.height = height
            content.layoutParams = params
        }
        content.x = (popupX - windowOffsetX).toFloat()
        content.y = (screenY - windowOffsetY).toFloat()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        content.layout(0, 0, width, height)
    }

    fun dismiss() {
        animator?.cancel()
        animator = null
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
     * Раскрытие меняет высоту карточки. Если места сверху хватает — верх
     * карточки плавно уезжает вверх одновременно с ростом высоты, чтобы
     * не было скачка «сначала список, потом перенос».
     */
    private fun animateToContent() {
        val anchor = anchorView ?: return
        if (!popup.isShowing) return
        val targetHeight = measureHeight()
        val target = positionFor(anchor, targetHeight)
        val startY = popupY
        val startHeight = popupHeight
        animator?.cancel()
        if (startY == target.y && startHeight == targetHeight) return
        val metrics = context.resources.displayMetrics
        val margin = (8 * density).toInt()
        val animation = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RESIZE_DURATION_MS
            interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            addUpdateListener { value ->
                val fraction = value.animatedValue as Float
                val height = (startHeight + (targetHeight - startHeight) * fraction).toInt()
                val y = PopupResize.yForHeight(
                    growsUp = growsUp,
                    edgeAnchor = edgeAnchor,
                    height = height,
                    screenHeight = metrics.heightPixels,
                    margin = margin
                )
                popupX = target.x
                popupY = y
                popupHeight = height
                applyCardGeometry(y, height)
            }
        }
        animator = animation
        animation.start()
    }

    private fun renderReactions() {
        val strip = content.findViewById<LinearLayout>(R.id.reactionsStrip)
        val recentRow = content.findViewById<LinearLayout>(R.id.recentReactions)
        val full = content.findViewById<GridLayout>(R.id.fullReactions)
        val arrow = content.findViewById<ImageButton>(R.id.btnExpandReactions)
        recentRow.removeAllViews()
        full.removeAllViews()
        val size = (ICON_DP * density).toInt()

        val recent = ReactionHistory.recent(context)
        // В полосе — только последние 11: короче прокрутка.
        recent.take(STRIP_LIMIT).forEach { emoji ->
            recentRow.addView(emojiView(emoji, size, grid = false))
        }

        // Раскрытие заменяет полосу общим списком: все реакции разом,
        // без дублей, сверху вниз. Сворачивать нечего — выбор эмодзи или
        // тап мимо закрывают карточку.
        ReactionOrder.ordered(recent, ReactionHistory.FULL).forEach { emoji ->
            full.addView(emojiView(emoji, size, grid = true))
        }
        arrow.setOnClickListener {
            if (expanded) return@setOnClickListener
            expanded = true
            strip.visibility = View.GONE
            full.visibility = View.VISIBLE
            animateToContent()
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
        val scroll = content.findViewById<ScrollView>(R.id.statusScroll).apply {
            isVerticalScrollBarEnabled = false
        }
        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        readers.removeAllViews()
        rows.removeAllViews()
        scroll.visibility = View.GONE
        statusExpanded = false

        val readRows = statusRows.filter { it.status == "READ" }
        val hasReactions = reactions.isNotEmpty()
        val hasReaders = showStatus && readRows.isNotEmpty()
        // На чужих сообщениях получателей нет — показываем авторов реакций.
        val detailsRows = if (statusRows.isNotEmpty()) {
            statusRows + reactionRows.filterNot { author ->
                statusRows.any { it.userUuid == author.userUuid }
            }
        } else {
            reactionRows
        }
        // Реакции и прочитавшие живут в одном блоке «Детали».
        section.visibility =
            if (hasReactions || hasReaders || reactionRows.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        if (section.visibility != View.VISIBLE) return

        // Свёрнутый вид — текстовый заголовок, а не аватарки: в ряд они
        // сливаются в кашу, список показываем по тапу.
        readers.addView(TextView(context).apply {
            text = context.getString(R.string.message_status_title)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                com.google.android.material.color.MaterialColors.getColor(
                    content, com.google.android.material.R.attr.colorPrimary
                )
            )
            setPadding((6 * density).toInt(), (2 * density).toInt(), 0, 0)
        })
        readers.setOnClickListener { setStatusExpanded(!statusExpanded) }

        // Строки получателей: аватар, имя и под ним время со значком статуса;
        // справа — реакции, которые поставил именно этот человек.
        val reactionsByUser = ReactionsByUser.map(reactions)
        val onSurface = com.google.android.material.color.MaterialColors.getColor(
            content, com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = androidx.core.graphics.ColorUtils.setAlphaComponent(
            onSurface, 0x99
        )
        detailsRows.forEach { row ->
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (6 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener { setStatusExpanded(false) }
            }
            line.addView(ImageView(context).apply {
                val size = (34 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (9 * density).toInt()
                }
                com.example.muzea.utils.AvatarLoader.load(this, row.avatarUrl)
            })

            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            column.addView(TextView(context).apply {
                text = row.name
                textSize = 15f
                setTextColor(onSurface)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val meta = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val top = (2 * density).toInt()
                setPadding(0, top, 0, 0)
            }
            meta.addView(TextView(context).apply {
                text = StatusTime.format(row.at)
                textSize = 12f
                setTextColor(onSurfaceVariant)
            })
            if (row.status != "REACTED") {
                val (statusIcon, statusColor) = when (row.status) {
                    "READ" -> R.drawable.ic_message_read to R.color.teal_700
                    "DELIVERED" -> R.drawable.ic_message_delivered to R.color.gray
                    else -> R.drawable.ic_message_sent to R.color.gray
                }
                meta.addView(ImageView(context).apply {
                    val size = (14 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginStart = (5 * density).toInt()
                    }
                    setImageResource(statusIcon)
                    setColorFilter(
                        androidx.core.content.ContextCompat.getColor(context, statusColor)
                    )
                })
            }
            column.addView(meta)
            line.addView(column)

            val (shown, hidden) = ReactionsByUser.visible(
                reactionsByUser[row.userUuid].orEmpty()
            )
            if (shown.isNotEmpty()) {
                val reactionRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val start = (8 * density).toInt()
                    setPadding(start, 0, 0, 0)
                }
                shown.forEach { emoji ->
                    reactionRow.addView(TextView(context).apply {
                        text = emoji
                        textSize = 17f
                        includeFontPadding = false
                        val gap = (2 * density).toInt()
                        setPadding(gap, 0, gap, 0)
                    })
                }
                if (hidden > 0) {
                    reactionRow.addView(TextView(context).apply {
                        text = "+$hidden"
                        textSize = 12f
                        setTextColor(onSurfaceVariant)
                        val start = (3 * density).toInt()
                        setPadding(start, 0, 0, 0)
                    })
                }
                line.addView(reactionRow)
            }
            rows.addView(line)
        }
        scroll.setOnClickListener { setStatusExpanded(false) }
        rows.setOnClickListener { setStatusExpanded(false) }
        // Четыре с половиной строки: половина пятой подсказывает прокрутку.
        val row = (STATUS_ROW_DP * density).toInt()
        StatusListSize.limitHeightPx(rows.childCount, row)?.let { limit ->
            scroll.layoutParams = scroll.layoutParams.apply { height = limit }
        }
    }

    private fun setStatusExpanded(expand: Boolean) {
        if (statusExpanded == expand) return
        statusExpanded = expand
        val scroll = content.findViewById<ScrollView>(R.id.statusScroll).apply {
            isVerticalScrollBarEnabled = false
        }
        scroll.visibility = if (expand) View.VISIBLE else View.GONE
        animateToContent()
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
