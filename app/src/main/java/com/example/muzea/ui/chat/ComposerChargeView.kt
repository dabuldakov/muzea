package com.example.muzea.ui.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/**
 * Заряд кнопки отправки, нарисованный по всему композеру.
 *
 * Пока палец держит кнопку, её саму не видно, поэтому сила показывается
 * рамкой композера: жёлтый контур с ореолом и короткими разрядами по кромке.
 * Чем ближе к 100 %, тем ярче контур и чаще разряды. После отпускания —
 * короткая вспышка по всей ширине.
 */
class ComposerChargeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val random = Random(System.nanoTime())
    private val density = resources.displayMetrics.density

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFD400")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFD400")
        maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD400")
    }
    private val rect = RectF()
    private val path = Path()

    private var tickAnimator: ValueAnimator? = null
    private var ticks = 0
    private var burstProgress = 0f

    var power: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    init {
        // BlurMaskFilter работает только в программном слое.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun updatePower(power: Int) {
        this.power = power
        if (power > 0) startTicking()
    }

    private fun startTicking() {
        if (tickAnimator?.isRunning == true) return
        tickAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 130
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ticks++
                invalidate()
            }
            start()
        }
    }

    /** Вспышка по всей ширине композера после отправки. */
    fun burst(targetPower: Int) {
        this.power = targetPower.coerceIn(0, 100)
        tickAnimator?.cancel()
        tickAnimator = null
        burstProgress = 1f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 420
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Иначе вспышка остаётся на экране: power уже неактуален.
                    this@ComposerChargeView.power = 0
                    burstProgress = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    fun clear() {
        tickAnimator?.cancel()
        tickAnimator = null
        burstProgress = 0f
        power = 0
        invalidate()
    }

    fun release() {
        tickAnimator?.cancel()
        tickAnimator = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (power <= 0 && burstProgress <= 0f) return
        val level = if (power > 0) power / 100f else 0f
        val intensity = maxOf(level, burstProgress)
        if (intensity <= 0f) return

        val inset = 3f * density
        rect.set(inset, inset, width - inset, height - inset)
        val radius = 6f * density

        glowPaint.strokeWidth = (6f + 7f * intensity) * density
        glowPaint.alpha = (55 + 135 * intensity).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, glowPaint)

        borderPaint.strokeWidth = (2f + 3f * intensity) * density
        borderPaint.alpha = (90 + 165 * intensity).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Короткие разряды по верхней и нижней кромке.
        val bolts = 2 + (intensity * 7).toInt()
        sparkPaint.alpha = (110 + 145 * intensity).toInt().coerceIn(0, 255)
        repeat(bolts) {
            val alongTop = random.nextBoolean()
            val x = random.nextFloat() * width
            val baseY = if (alongTop) inset else height - inset
            drawSpark(canvas, x, baseY, if (alongTop) -1f else 1f, intensity)
        }
    }

    private fun drawSpark(canvas: Canvas, x: Float, baseY: Float, direction: Float, intensity: Float) {
        val length = (5f + 9f * intensity + random.nextFloat() * 5f) * density
        val step = 2.4f * density
        path.reset()
        path.moveTo(x, baseY)
        var cx = x
        var cy = baseY
        var side = if (random.nextBoolean()) 1f else -1f
        var travelled = 0f
        while (travelled < length) {
            travelled += step
            cy += direction * step
            cx += side * step * (0.6f + random.nextFloat() * 0.7f)
            side = -side
            path.lineTo(cx, cy)
        }
        canvas.drawPath(path, borderPaint)

        // Пара искр вокруг разряда.
        val sparks = 1 + (intensity * 3).toInt()
        repeat(sparks) {
            val sx = x + (random.nextFloat() - 0.5f) * 14f * density
            val sy = baseY + direction * random.nextFloat() * length
            canvas.drawCircle(sx, sy, (0.8f + 1.4f * intensity) * density, sparkPaint)
        }
    }
}
