package com.example.muzea.ui.chat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Заряд кнопки отправки, нарисованный по всему композеру.
 *
 * Пока палец держит кнопку, её саму не видно, поэтому сила показывается
 * композером: тонкая жёлтая рамка и искры, бегущие по его фону. Чем ближе
 * к 100 %, тем больше искр и ярче рамка. После отпускания фон коротко
 * вспыхивает жёлтым.
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
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD400")
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD400")
    }
    private val rect = RectF()

    private val sparks = mutableListOf<Spark>()
    private var tickAnimator: ValueAnimator? = null
    private var burstProgress = 0f
    /** Текущий заряд 0..100; только для чтения из тестов и UI. */
    var power = 0
        private set

    private class Spark(
        var x: Float,
        var y: Float,
        var speed: Float,
        var life: Float,
        var radius: Float
    )

    fun updatePower(value: Int) {
        power = value.coerceIn(0, 100)
        if (power > 0) startTicking()
        invalidate()
    }

    fun burst(value: Int) {
        power = value.coerceIn(0, 100)
        tickAnimator?.cancel()
        tickAnimator = null
        sparks.clear()
        burstProgress = 1f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 520
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    burstProgress = 0f
                    power = 0
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
        sparks.clear()
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

    private fun startTicking() {
        if (tickAnimator?.isRunning == true) return
        tickAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                stepSparks()
                invalidate()
            }
            start()
        }
    }

    private fun stepSparks() {
        val level = power / 100f
        if (level <= 0f) return
        val spawn = 3 + (level * 7).toInt()
        repeat(spawn) {
            if (sparks.size < MAX_SPARKS) {
                sparks += Spark(
                    x = random.nextFloat() * width,
                    y = height * (0.45f + random.nextFloat() * 0.55f),
                    speed = (4f + 9f * random.nextFloat()) * density * (0.7f + level),
                    life = 1f,
                    radius = (1.3f + 1.6f * random.nextFloat()) * density * (0.8f + 0.6f * level)
                )
            }
        }
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val spark = iterator.next()
            spark.y -= spark.speed
            spark.life -= 0.05f + 0.04f * (1f - level)
            if (spark.life <= 0f || spark.y < 0f) iterator.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val level = power / 100f
        if (burstProgress > 0f) {
            // Вспышка: фон композера заливается жёлтым и гаснет.
            flashPaint.alpha = (150 * burstProgress).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
        if (level <= 0f) return

        val inset = 3f * density
        rect.set(inset, inset, width - inset, height - inset)
        val radius = 6f * density
        borderPaint.strokeWidth = (1.2f + 1.4f * level) * density
        borderPaint.alpha = (90 + 130 * level).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Искры бегут по фону композера, а не по контуру: точка + короткий след.
        sparks.forEach { spark ->
            sparkPaint.alpha = (spark.life * 245).toInt().coerceIn(0, 255)
            canvas.drawCircle(spark.x, spark.y, spark.radius, sparkPaint)
            sparkPaint.alpha = (spark.life * 120).toInt().coerceIn(0, 255)
            canvas.drawLine(
                spark.x, spark.y,
                spark.x, spark.y + spark.radius * 2.6f,
                sparkPaint
            )
        }
    }

    private companion object {
        const val MAX_SPARKS = 64
    }
}
