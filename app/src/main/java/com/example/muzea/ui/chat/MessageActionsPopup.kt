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
    /** Ниже какого Y карточка не должна подниматься (низ шапки чата). */
    private val topLimit: Int = 0,
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
        // Тонкая линия-разделитель между реакциями и действиями: цвет берём
        // из темы, чтобы читалась и в тёмной теме.
        content.findViewById<View>(R.id.actionsDivider)?.let { divider ->
            val onSurface = com.google.android.material.color.MaterialColors.getColor(
                content, com.google.android.material.R.attr.colorOnSurface
            )
            divider.setBackgroundColor(
                androidx.core.graphics.ColorUtils.setAlphaComponent(onSurface, 0x1F)
            )
        }
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
    private val details = DetailsSection(
        context = context,
        content = content,
        density = context.resources.displayMetrics.density,
        onHeaderClick = { setStatusExpanded(!statusExpanded) },
        onRowClick = { if (statusExpanded) setStatusExpanded(false) }
    )
    private var growsUp = false
    private var edgeAnchor = 0
    private var windowOffsetX = 0
    private var windowOffsetY = 0
    private var animator: android.animation.ValueAnimator? = null

    private companion object {
        const val ICON_DP = 38
        const val STRIP_LIMIT = 11
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
        // Показываем у точки нажатия, а не по центру пузыря. Для сообщений
        // в верхней половине экрана карточку ставим строго под пальцем:
        // иначе её верх уезжает под строку состояния.
        val fingerY = tapY ?: location[1]
        val minY = topLimit.coerceAtLeast(margin)
        if (fingerY < metrics.heightPixels / 2) {
            val below = (fingerY + margin)
                .coerceAtMost((metrics.heightPixels - height - margin).coerceAtLeast(minY))
                .coerceAtLeast(minY)
            return ActionsPopupPosition.Point(
                x = (tapX ?: (location[0] + anchor.width / 2) - width / 2)
                    .coerceIn(margin, (metrics.widthPixels - width - margin).coerceAtLeast(margin)),
                y = below
            )
        }
        val position = ActionsPopupPosition.calculate(
            tapX = tapX ?: (location[0] + anchor.width / 2),
            tapY = fingerY,
            popupWidth = width,
            popupHeight = height,
            screenWidth = metrics.widthPixels,
            margin = margin,
            screenHeight = metrics.heightPixels
        )
        return position.copy(y = position.y.coerceAtLeast(minY))
    }

    /**
     * Раскрытие меняет высоту карточки. Если места сверху хватает — верх
     * карточки плавно уезжает вверх одновременно с ростом высоты, чтобы
     * не было скачка «сначала список, потом перенос».
     */
    /**
     * Анимирует изменение содержимого карточки. На каждом кадре вызывается
     * [onFraction], который меняет высоту внутреннего блока (список, сетка),
     * затем карточка пересчитывается по фактической высоте. Край, закреплённый
     * при показе, не двигается.
     */
    private fun animateContent(onFraction: (Float) -> Unit, onEnd: (() -> Unit)? = null) {
        if (!popup.isShowing) return
        animator?.cancel()
        val metrics = context.resources.displayMetrics
        val margin = (8 * density).toInt()
        val animation = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RESIZE_DURATION_MS
            interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            addUpdateListener { value ->
                onFraction(value.animatedFraction)
                val height = measureHeight()
                val y = PopupResize.yForHeight(
                    growsUp = growsUp,
                    edgeAnchor = edgeAnchor,
                    height = height,
                    screenHeight = metrics.heightPixels,
                    margin = margin,
                    topLimit = topLimit
                )
                popupY = y
                popupHeight = height
                applyCardGeometry(y, height)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
            })
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
            val stripHeight = (44 * density).toInt()
            val cell = (ICON_DP * density)
            val columns = 7
            val rows = (ReactionOrder.ordered(recent, ReactionHistory.FULL).size +
                columns - 1) / columns
            val gridHeight = (rows * cell).toInt()
            // Значки в сетке стоят на том же отступе от верха, что и в полосе.
            val topMargin = (full.layoutParams as? ViewGroup.MarginLayoutParams)
                ?.topMargin ?: 0
            val startHeight = (stripHeight - topMargin).coerceAtLeast(0)
            full.layoutParams = full.layoutParams.apply { height = startHeight }
            strip.visibility = View.GONE
            full.visibility = View.VISIBLE
            animateContent(onFraction = { fraction ->
                val h = (startHeight + (gridHeight - startHeight) * fraction).toInt()
                full.layoutParams = full.layoutParams.apply { height = h }
            })
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
        details.render(
            statusRows = statusRows,
            reactions = reactions,
            reactionRows = reactionRows,
            showStatus = showStatus
        )
    }

    private fun setStatusExpanded(expand: Boolean) {
        if (statusExpanded == expand) return
        statusExpanded = expand
        val scroll = details.scrollView()
        val listHeight = details.listHeight.coerceAtLeast(0)
        if (expand) {
            scroll.layoutParams = scroll.layoutParams.apply { height = 0 }
            scroll.visibility = View.VISIBLE
            animateContent(onFraction = { fraction ->
                scroll.layoutParams = scroll.layoutParams.apply {
                    height = (listHeight * fraction).toInt()
                }
            })
            return
        }
        // Сворачивание: список остаётся видимым, пока карточка уменьшается,
        // и скрываем его только в самом конце.
        val startHeight = scroll.height.takeIf { it > 0 } ?: listHeight
        animateContent(
            onFraction = { fraction ->
                scroll.layoutParams = scroll.layoutParams.apply {
                    height = (startHeight * (1f - fraction)).toInt()
                }
            },
            onEnd = { scroll.visibility = View.GONE }
        )
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
