package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.example.muzea.utils.ThemeManager

/**
 * Комикс-оформление чата для тем Pink и Violet.
 *
 * Стандартная тема возвращает null и остаётся нетронутой. Здесь только код и
 * Canvas: угловатые баблы с хвостами, толстые контуры, плашки кнопок.
 */
object ComicStyle {

    data class Palette(
        val background: Int,
        val surface: Int,
        val incoming: Int,
        val outgoing: Int,
        val incomingText: Int,
        val outgoingText: Int,
        val accent: Int
    )

    fun palette(context: Context): Palette? = when (ThemeManager.current(context)) {
        ThemeManager.PINK -> Palette(
            background = Color.parseColor("#F6E7C6"),
            surface = Color.parseColor("#FFF3D6"),
            incoming = Color.parseColor("#FFF3D6"),
            outgoing = Color.parseColor("#F5187F"),
            incomingText = Color.parseColor("#151515"),
            outgoingText = Color.parseColor("#151515"),
            accent = Color.parseColor("#F5187F")
        )
        ThemeManager.VIOLET -> Palette(
            background = Color.parseColor("#1B0F2B"),
            surface = Color.parseColor("#2A2A2E"),
            incoming = Color.parseColor("#2A2A2E"),
            outgoing = Color.parseColor("#8B2CF5"),
            incomingText = Color.WHITE,
            outgoingText = Color.WHITE,
            accent = Color.parseColor("#8B2CF5")
        )
        else -> null
    }

    fun bubble(context: Context, incoming: Boolean): Drawable? {
        val palette = palette(context) ?: return null
        return ComicBubbleDrawable(
            fill = if (incoming) palette.incoming else palette.outgoing,
            incoming = incoming,
            density = context.resources.displayMetrics.density
        )
    }

    fun header(context: Context): Drawable? {
        val palette = palette(context) ?: return null
        return panel(palette.accent, context, bottomRadius = false)
    }

    fun panel(context: Context): Drawable? {
        val palette = palette(context) ?: return null
        return panel(palette.surface, context, bottomRadius = false)
    }

    fun field(context: Context): Drawable {
        val palette = palette(context) ?: return GradientDrawable()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(palette.surface)
            setStroke(dp(context, 3f), Color.BLACK)
        }
    }

    fun circleBorder(context: Context): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(dp(context, 3f), Color.BLACK)
    }

    fun button(context: Context, filled: Boolean): Drawable {
        val palette = palette(context)
        val fill = when {
            !filled && palette != null -> palette.surface
            palette != null -> palette.accent
            else -> Color.TRANSPARENT
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(fill)
            setStroke(dp(context, 3f), Color.BLACK)
        }
    }

    private fun panel(color: Int, context: Context, bottomRadius: Boolean): Drawable {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(color)
        }
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 3f), Color.BLACK)
        }
        return LayerDrawable(arrayOf(background, border))
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

/** Бабл с хвостом влево (входящее) или вправо (исходящее). */
class ComicBubbleDrawable(
    private val fill: Int,
    private val incoming: Boolean,
    private val density: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.BLACK
    }
    private val rect = RectF()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val tail = 7f * density
        val shadow = 4f * density
        rect.set(
            bounds.left + (if (incoming) 0f else tail),
            bounds.top.toFloat(),
            bounds.right - (if (incoming) tail else 0f),
            bounds.bottom - shadow
        )
        buildPath(rect, tail)
        // Тень.
        canvas.save()
        canvas.translate(shadow, shadow)
        paint.color = Color.BLACK
        canvas.drawPath(path, paint)
        canvas.restore()
        // Заливка и контур.
        paint.color = fill
        canvas.drawPath(path, paint)
        canvas.drawPath(path, stroke)
    }

    private fun buildPath(r: RectF, tail: Float) {
        path.reset()
        val radius = 6f * density
        path.addRoundRect(r, radius, radius, Path.Direction.CW)
        val tipY = r.bottom - 12f * density
        if (incoming) {
            path.moveTo(r.left, tipY)
            path.lineTo(r.left - tail, tipY + tail)
            path.lineTo(r.left, tipY + tail * 1.6f)
        } else {
            path.moveTo(r.right, tipY)
            path.lineTo(r.right + tail, tipY + tail)
            path.lineTo(r.right, tipY + tail * 1.6f)
        }
        path.close()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
