package com.example.muzea.ui.chat

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R

/**
 * Прокрутка к сообщению из push и короткая подсветка пузыря.
 *
 * Подсветка — полупрозрачный foreground: рисуется поверх содержимого и не
 * ломает скругления пузыря.
 */
internal class ChatMessageHighlighter(
    private val recyclerView: RecyclerView,
    private val topOffsetPx: Int
) {

    private val density = recyclerView.resources.displayMetrics.density

    fun scrollToAndHighlight(position: Int, uuid: String) {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(position, topOffsetPx)
            attempt(position, uuid, attempt = 0)
        }
    }

    private fun attempt(position: Int, uuid: String, attempt: Int) {
        recyclerView.postDelayed({
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            if (holder == null) {
                // Строка могла ещё не разложиться после прокрутки.
                if (attempt < HIGHLIGHT_ATTEMPTS) {
                    attempt(position, uuid, attempt + 1)
                }
                return@postDelayed
            }
            val row = holder.itemView
            // Вспышка — по самому пузырю, а не по всей строке: у пузыря
            // скругления, и цвет остаётся в его границах.
            val bubble = row.findViewById<View>(R.id.tvMessage)
                ?.takeIf { it.visibility == View.VISIBLE }
                ?: row
            bubble.tag = uuid
            bubble.foreground = highlightDrawable()
            bubble.postDelayed({
                if (bubble.tag == uuid) {
                    bubble.foreground = null
                    bubble.tag = null
                }
            }, HIGHLIGHT_MS)
        }, if (attempt == 0) 250L else 120L)
    }

    private fun highlightDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16 * density
            setColor(HIGHLIGHT_COLOR)
        }

    private companion object {
        const val HIGHLIGHT_MS = 1800L
        const val HIGHLIGHT_ATTEMPTS = 5
        const val HIGHLIGHT_COLOR = 0x557C3AED
    }
}
