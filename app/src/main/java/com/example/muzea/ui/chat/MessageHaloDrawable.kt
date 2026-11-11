package com.example.muzea.ui.chat

import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Горящий ареол вокруг сообщения с зарядом.
 *
 * Рисуется как foreground контейнера бабла: свечение и молнии идут по внешней
 * стороне рамки, поэтому текст не перекрывается. Живёт 2,5 секунды.
 */
class MessageHaloDrawable(
    private val power: Int,
    private val density: Float
) : Drawable() {

    private val level = power.coerceIn(0, 100) / 100f
    private val random = Random(System.nanoTime())
    private val rect = RectF()
    private val path = Path()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFFFB020")
        maskFilter = BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFFFD400")
    }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFF8A18")
    }

    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun start() {
        animator?.cancel()
        progress = 0f
        // В чистом JVM-тесте ValueAnimator недоступен: анимацию просто пропускаем.
        animator = runCatching {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        animator?.cancel()
        animator = null
        progress = 0f
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f || level <= 0f) return
        val envelope = when {
            progress < 0.12f -> progress / 0.12f
            progress > 0.7f -> (1f - progress) / 0.3f
            else -> 1f
        }.coerceIn(0f, 1f)
        val strength = level * envelope
        if (strength <= 0f) return

        // Бабл занимает bounds минус 8dp отступов; свечение рисуем снаружи.
        val inset = 8f * density
        rect.set(
            bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset
        )

        glowPaint.strokeWidth = (2f + 7f * strength) * density
        glowPaint.alpha = (80 + 150 * strength).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, 16f * density, 16f * density, glowPaint)

        boltPaint.strokeWidth = (1.4f + 1.6f * strength) * density
        boltPaint.alpha = (150 + 105 * strength).toInt().coerceIn(0, 255)
        repeat(2 + (strength * 5).toInt()) { drawBolt(canvas, strength) }

        repeat(3 + (strength * 8).toInt()) { drawEmber(canvas, strength) }
    }

    private fun drawBolt(canvas: Canvas, strength: Float) {
        val side = random.nextInt(4)
        var x: Float
        var y: Float
        when (side) {
            0 -> { x = rect.left + random.nextFloat() * rect.width(); y = rect.top - 2f * density }
            1 -> { x = rect.left + random.nextFloat() * rect.width(); y = rect.bottom + 2f * density }
            2 -> { x = rect.left - 2f * density; y = rect.top + random.nextFloat() * rect.height() }
            else -> { x = rect.right + 2f * density; y = rect.top + random.nextFloat() * rect.height() }
        }
        val steps = 4
        val length = (6f + 9f * strength) * density
        var angle = random.nextFloat() * 6.28f
        path.reset()
        path.moveTo(x, y)
        repeat(steps) {
            x += cos(angle) * length / steps
            y += sin(angle) * length / steps
            angle += (random.nextFloat() - 0.5f) * 1.4f
            path.lineTo(x, y)
        }
        canvas.drawPath(path, boltPaint)
    }

    private fun drawEmber(canvas: Canvas, strength: Float) {
        val phase = (progress * 2f) % 1f
        val side = random.nextInt(4)
        val x = when (side) {
            0 -> rect.left - 4f * density
            1 -> rect.right + 4f * density
            else -> rect.left + random.nextFloat() * rect.width()
        }
        val y = when (side) {
            0, 1 -> rect.top + random.nextFloat() * rect.height()
            2 -> rect.top - 4f * density - phase * 10f * density
            else -> rect.bottom + 4f * density + phase * 10f * density
        }
        emberPaint.alpha = ((1f - phase) * 220 * strength).toInt().coerceIn(0, 255)
        val radius = (0.8f + random.nextFloat() * 1.4f) * density * (0.7f + strength)
        canvas.drawCircle(x + sin(phase * 6f) * 3f * density, y, radius, emberPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
    }

    private companion object {
        const val ANIMATION_MS = 2500L
    }
}
