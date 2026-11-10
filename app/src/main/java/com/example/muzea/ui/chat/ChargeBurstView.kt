package com.example.muzea.ui.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Визуализация «заряда» кнопки отправки: молнии и искры вокруг неё.
 *
 * Рисуем на Canvas без Lottie: несколько ломаных молний и разлетающихся
 * искр. Чем больше [power], тем больше ветвей и ярче свечение. На отпускании
 * [burst] коротко разлетается и гаснет.
 */
class ChargeBurstView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val random = Random(System.nanoTime())
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFEB3B")
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFC107")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33FFEB3B")
    }
    private val path = Path()
    private var tickAnimator: ValueAnimator? = null
    private var ticks = 0
    private var burstProgress = 0f

    var power: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    fun updatePower(power: Int) {
        this.power = power
        if (power > 0) startTicking()
    }

    private fun startTicking() {
        if (tickAnimator?.isRunning == true) return
        tickAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 120
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ticks++
                invalidate()
            }
            start()
        }
    }

    /** Финальный разлёт при отправке. */
    fun burst(power: Int) {
        this.power = power.coerceIn(0, 100)
        tickAnimator?.cancel()
        tickAnimator = null
        burstProgress = 1f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 450
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
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
        val burstFactor = if (burstProgress > 0f) burstProgress else 0f
        val intensity = maxOf(level, burstFactor)
        if (intensity <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        // Начинаем у края кнопки (42dp => радиус 21dp) и уходим наружу.
        val baseRadius = 20f * density
        val reach = baseRadius + 32f * density * intensity * (1f + 0.6f * burstFactor)

        glowPaint.alpha = (40 + 90 * intensity).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, reach * 0.75f, glowPaint)

        // Молнии: от 3 до 11 ветвей, тем больше, чем сильнее заряд.
        val branches = 3 + (intensity * 8).toInt()
        boltPaint.strokeWidth = 2f + 4.5f * intensity
        corePaint.strokeWidth = 1f + 2f * intensity
        repeat(branches) { index ->
            val angle = -Math.PI / 2 + (index - branches / 2f) * 0.55f +
                random.nextFloat() * 0.25f
            drawBolt(canvas, cx, cy, baseRadius * 0.55f, reach, angle, intensity)
        }

        // Искры разлетаются по кругу; при burst — дальше.
        val sparks = 6 + (intensity * 22).toInt()
        repeat(sparks) {
            val angle = random.nextFloat() * (2 * Math.PI).toFloat()
            val distance = baseRadius + (reach - baseRadius) * random.nextFloat()
            val x = cx + cos(angle) * distance
            val y = cy + sin(angle) * distance
            val radius = 1.2f + 2.4f * intensity
            canvas.drawCircle(x, y, radius, sparkPaint)
        }
    }

    private fun drawBolt(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        startRadius: Float,
        endRadius: Float,
        angle: Double,
        intensity: Float
    ) {
        val segments = 4
        path.reset()
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val radius = startRadius + (endRadius - startRadius) * t
            val jitter = if (i == 0 || i == segments) 0f else (random.nextFloat() - 0.5f) * endRadius * 0.35f
            val x = cx + cos(angle).toFloat() * radius +
                cos(angle + Math.PI / 2).toFloat() * jitter
            val y = cy + sin(angle).toFloat() * radius +
                sin(angle + Math.PI / 2).toFloat() * jitter
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, boltPaint)
        // Белая сердцевина — «электрический» вид.
        canvas.drawPath(path, corePaint)
    }
}
