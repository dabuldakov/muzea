package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.muzea.R

/**
 * Секция реакций в карточке: короткая полоса со стрелкой и раскрытая сетка.
 * Не знает про окно и позиционирование — раскрытие отдаёт наружу через
 * [animate], чтобы карточка могла синхронно менять высоту.
 */
class ReactionsSection(
    private val context: Context,
    content: View,
    private val density: Float,
    private val onReaction: (String) -> Unit,
    private val animate: (onFraction: (Float) -> Unit) -> Unit
) {
    private val strip = content.findViewById<LinearLayout>(R.id.reactionsStrip)
    private val recentRow = content.findViewById<LinearLayout>(R.id.recentReactions)
    private val full = content.findViewById<GridLayout>(R.id.fullReactions)
    private val arrow = content.findViewById<ImageButton>(R.id.btnExpandReactions)

    var isExpanded: Boolean = false
        private set

    fun render(recent: List<String>, all: List<String>) {
        recentRow.removeAllViews()
        full.removeAllViews()
        val size = (ICON_DP * density).toInt()

        recent.take(STRIP_LIMIT).forEach { emoji ->
            recentRow.addView(emojiView(emoji, size, grid = false))
        }
        all.forEach { emoji ->
            full.addView(emojiView(emoji, size, grid = true))
        }

        arrow.setOnClickListener {
            if (isExpanded) return@setOnClickListener
            isExpanded = true
            expand(size)
        }
    }

    /** Раскрытие заменяет полосу общим списком; высота сетки растёт анимацией. */
    private fun expand(cell: Int) {
        val stripHeight = (STRIP_HEIGHT_DP * density).toInt()
        val columns = 7
        val rows = (full.childCount + columns - 1) / columns
        val gridHeight = rows * cell
        // Значки в сетке стоят на том же отступе от верха, что и в полосе.
        val topMargin = (full.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.topMargin ?: 0
        val startHeight = (stripHeight - topMargin).coerceAtLeast(0)
        full.layoutParams = full.layoutParams.apply { height = startHeight }
        strip.visibility = View.GONE
        full.visibility = View.VISIBLE
        animate { fraction ->
            val h = (startHeight + (gridHeight - startHeight) * fraction).toInt()
            full.layoutParams = full.layoutParams.apply { height = h }
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
            }
        }

    companion object {
        const val ICON_DP = 38
        const val STRIP_LIMIT = 11
        const val STRIP_HEIGHT_DP = 44
    }
}
