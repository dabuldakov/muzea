package com.example.muzea.ui.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.random.Random

/**
 * Бабл с накопленным повреждением: трещины, обугленный контур и тлеющие искры.
 *
 * Рисуем сами, без растровых ассетов. Трещины детерминированы по seed (uuid
 * сообщения), поэтому при прокрутке не «перерисовываются» каждый раз.
 * Текст остаётся читаемым: затемнение ограничено, контраст текста сохраняется.
 */
class DamagedBubbleDrawable(
    private val outgoing: Boolean,
    damage: Int,
    seed: Long,
    private val incomingColor: Int,
    private val outgoingColor: Int,
    private val density: Float
) : Drawable() {

    private val level = (damage.coerceIn(0, 100)) / 100f
    private val random = Random(seed)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val crack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1A0B0714")
    }
    private val ember = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFF7A18")
    }
    private val rect = RectF()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val shadow = 4f * density
        val dmg = level

        // Жёсткая тень.
        rect.set(
            b.left + shadow, b.top + shadow,
            b.right.toFloat(), b.bottom.toFloat()
        )
        fill.color = Color.BLACK
        canvas.drawPath(roundedPath(rect), fill)

        // Заливка бабла.
        rect.set(
            b.left.toFloat(), b.top.toFloat(),
            b.right - shadow, b.bottom - shadow
        )
        fill.color = blend(if (outgoing) outgoingColor else incomingColor, CHAR, 0.62f * dmg)
        canvas.drawPath(roundedPath(rect), fill)

        // Контур 3dp.
        stroke.color = Color.BLACK
        stroke.strokeWidth = 3f * density
        canvas.drawPath(roundedPath(rect), stroke)

        // Трещины от краёв к центру.
        val cracks = 3 + (dmg * 6).toInt()
        repeat(cracks) { drawCrack(canvas, rect, dmg) }

        // Тлеющие искры на сильных повреждениях.
        if (dmg > 0.35f) {
            val embers = (2 + dmg * 8).toInt()
            repeat(embers) {
                val x = rect.left + random.nextFloat() * rect.width()
                val y = rect.top + random.nextFloat() * rect.height()
                ember.alpha = (90 + 150 * dmg).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, (0.6f + random.nextFloat() * 1.2f) * density, ember)
            }
        }
    }

    private fun drawCrack(canvas: Canvas, r: RectF, dmg: Float) {
        val side = random.nextInt(4)
        val startX: Float
        val startY: Float
        when (side) {
            0 -> { startX = r.left; startY = r.top + random.nextFloat() * r.height() }
            1 -> { startX = r.right; startY = r.top + random.nextFloat() * r.height() }
            2 -> { startX = r.left + random.nextFloat() * r.width(); startY = r.top }
            else -> { startX = r.left + random.nextFloat() * r.width(); startY = r.bottom }
        }
        val segments = 4 + random.nextInt(3)
        val length = (10f + 26f * random.nextFloat()) * density * (0.6f + dmg)
        var angle = random.nextFloat() * (Math.PI * 2).toFloat()
        var x = startX
        var y = startY
        path.reset()
        path.moveTo(x, y)
        repeat(segments) { i ->
            val step = length / segments
            x += kotlin.math.cos(angle) * step
            y += kotlin.math.sin(angle) * step
            angle += (random.nextFloat() - 0.5f) * 1.2f
            path.lineTo(
                x.coerceIn(r.left + 2, r.right - 2),
                y.coerceIn(r.top + 2, r.bottom - 2)
            )
        }
        crack.strokeWidth = (1.1f + 1.6f * dmg) * density
        crack.alpha = (140 + 100 * dmg).toInt().coerceIn(0, 255)
        canvas.drawPath(path, crack)
    }

    private fun roundedPath(r: RectF): Path {
        val big = 18f * density
        val small = 4f * density
        val radii = if (outgoing) {
            floatArrayOf(big, big, big, big, small, small, big, big)
        } else {
            floatArrayOf(0f, 0f, big, big, big, big, big, big)
        }
        val shape = Path()
        shape.addRoundRect(r, radii, Path.Direction.CW)
        return shape
    }

    private fun blend(base: Int, overlay: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val r = (Color.red(base) * (1 - f) + Color.red(overlay) * f).toInt()
        val g = (Color.green(base) * (1 - f) + Color.green(overlay) * f).toInt()
        val b = (Color.blue(base) * (1 - f) + Color.blue(overlay) * f).toInt()
        return Color.rgb(r, g, b)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    private companion object {
        const val CHAR = 0xFF120C20.toInt()
    }
}
