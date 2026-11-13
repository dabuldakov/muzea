package com.example.muzea.ui.chat

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.domain.model.MessageReaction

/**
 * Блок «Детали»: заголовок и список получателей с их реакциями.
 * Отвечает только за содержимое, раскрытием управляет владелец карточки.
 */
class DetailsSection(
    private val context: Context,
    private val content: View,
    private val density: Float,
    private val onHeaderClick: () -> Unit,
    private val onRowClick: () -> Unit
) {
    private val section = content.findViewById<LinearLayout>(R.id.statusSection)
    private val readers = content.findViewById<LinearLayout>(R.id.readersRow)
    private val scroll = content.findViewById<ScrollView>(R.id.statusScroll).apply {
        isVerticalScrollBarEnabled = false
    }
    private val rows = content.findViewById<LinearLayout>(R.id.statusRows)

    /** Высота списка, к которой анимируется раскрытие. */
    var listHeight: Int = 0
        private set

    var isVisible: Boolean = false
        private set

    fun render(
        statusRows: List<MessageStatusRow>,
        reactions: List<MessageReaction>,
        reactionRows: List<MessageStatusRow>,
        showStatus: Boolean
    ) {
        readers.removeAllViews()
        rows.removeAllViews()
        scroll.visibility = View.GONE

        // На чужих сообщениях получателей нет — показываем авторов реакций.
        // На чужих сообщениях получателей нет — показываем авторов реакций.
        val detailsRows = if (statusRows.isNotEmpty()) {
            statusRows + reactionRows.filterNot { author ->
                statusRows.any { it.userUuid == author.userUuid }
            }
        } else {
            reactionRows
        }
        // «Детали» показываем всегда: даже без реакций и читателей блок
        // остаётся на месте, чтобы карточка не меняла состав.
        isVisible = true
        section.visibility = View.VISIBLE

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
        readers.setOnClickListener { onHeaderClick() }

        val reactionsByUser = ReactionsByUser.map(reactions)
        val onSurface = com.google.android.material.color.MaterialColors.getColor(
            content, com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = androidx.core.graphics.ColorUtils.setAlphaComponent(
            onSurface, 0x99
        )
        if (detailsRows.isEmpty()) {
            rows.addView(TextView(context).apply {
                text = context.getString(R.string.details_empty_reactions)
                textSize = 13f
                setTextColor(onSurfaceVariant)
                val pad = (6 * density).toInt()
                setPadding(pad, pad, pad, pad)
            })
        } else {
            detailsRows.forEach { row ->
                rows.addView(
                    buildRow(
                        row,
                        reactionsByUser[row.userUuid].orEmpty(),
                        onSurface,
                        onSurfaceVariant
                    )
                )
            }
        }
        scroll.setOnClickListener { onRowClick() }
        rows.setOnClickListener { onRowClick() }

        val rowHeight = (STATUS_ROW_DP * density).toInt()
        val natural = rows.childCount * rowHeight
        val limit = StatusListSize.limitHeightPx(rows.childCount, rowHeight)
        listHeight = limit ?: natural
        if (limit != null) {
            scroll.layoutParams = scroll.layoutParams.apply { height = limit }
        }
    }

    private fun buildRow(
        row: MessageStatusRow,
        userReactions: List<String>,
        onSurface: Int,
        onSurfaceVariant: Int
    ): LinearLayout {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { onRowClick() }
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
            ellipsize = TextUtils.TruncateAt.END
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
        if (row.status != MessageDetailsRows.STATUS_REACTED) {
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

        val (shown, hidden) = ReactionsByUser.visible(userReactions)
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
        return line
    }

    fun scrollView(): ScrollView = scroll

    companion object {
        const val STATUS_ROW_DP = 56
    }
}
