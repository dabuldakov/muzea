package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.example.muzea.utils.ThemeManager

/**
 * Пузыри для комикс-тем: острые углы и толстый чёрный контур.
 * Стандартная тема остаётся на прежних drawable.
 */
object ComicBubbles {

    fun isComic(context: Context): Boolean =
        ThemeManager.current(context) != ThemeManager.STANDARD

    fun background(context: Context, outgoing: Boolean): Drawable? {
        if (!isComic(context)) return null
        val pink = ThemeManager.current(context) == ThemeManager.PINK
        val fill = when {
            outgoing && pink -> Color.parseColor("#F5187F")
            outgoing -> Color.parseColor("#8B2CF5")
            pink -> Color.parseColor("#FFF3D6")
            else -> Color.parseColor("#2A2A2E")
        }
        val radii = floatArrayOf(6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f)
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(fill)
            setStroke(dp(context, 3f), Color.BLACK)
        }
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(Color.BLACK)
        }
        return LayerDrawable(arrayOf(shadow, border)).apply {
            setLayerInset(0, dp(context, 4f), dp(context, 4f), 0, 0)
        }
    }

    fun textColor(context: Context, outgoing: Boolean): Int {
        if (!isComic(context)) return if (outgoing) Color.WHITE else Color.BLACK
        return if (ThemeManager.current(context) == ThemeManager.PINK) Color.parseColor("#151515") else Color.WHITE
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
