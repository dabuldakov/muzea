package com.example.muzea.ui.auth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.muzea.R

/**
 * Декоративный фон экранов входа: две тонкие волнистые линии, розовая и
 * фиолетовая, идут сверху вниз по правой части экрана. Рисуются кодом, чтобы
 * одинаково выглядеть на любой высоте и не зависеть от плотности экрана.
 */
class AuthWavesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val pink = ContextCompat.getColor(context, R.color.auth_pink)
    private val purple = ContextCompat.getColor(context, R.color.auth_purple)

    private val pinkPath = Path()
    private val purplePath = Path()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 10f * density
        alpha = 26
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.4f * density
        alpha = 210
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        buildPaths(width.toFloat(), height.toFloat())
    }

    private fun buildPaths(width: Float, height: Float) {
        pinkPath.reset()
        pinkPath.moveTo(width * 0.74f, -height * 0.04f)
        pinkPath.cubicTo(
            width * 1.01f, height * 0.16f,
            width * 0.54f, height * 0.34f,
            width * 0.79f, height * 0.52f
        )
        pinkPath.cubicTo(
            width * 1.02f, height * 0.69f,
            width * 0.55f, height * 0.84f,
            width * 0.83f, height * 1.04f
        )

        purplePath.reset()
        purplePath.moveTo(width * 0.94f, -height * 0.04f)
        purplePath.cubicTo(
            width * 0.64f, height * 0.19f,
            width * 1.05f, height * 0.43f,
            width * 0.74f, height * 0.61f
        )
        purplePath.cubicTo(
            width * 0.47f, height * 0.76f,
            width * 0.93f, height * 0.89f,
            width * 0.72f, height * 1.04f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        glowPaint.color = pink
        glowPaint.alpha = 26
        canvas.drawPath(pinkPath, glowPaint)
        glowPaint.color = purple
        glowPaint.alpha = 26
        canvas.drawPath(purplePath, glowPaint)

        linePaint.color = pink
        linePaint.alpha = 210
        canvas.drawPath(pinkPath, linePaint)
        linePaint.color = purple
        linePaint.alpha = 210
        canvas.drawPath(purplePath, linePaint)
    }
}
