package com.example.muzea.ui.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.random.Random

/**
 * Бабл с накопленным повреждением в текущем стиле приложения.
 *
 * Дизайн не меняем: та же заливка, контур и жёсткая тень. Добавляем только
 * «физику» — трещины как по стеклу, длинные разломы и отломанные куски по
 * кромке. Трещины детерминированы по seed (uuid сообщения), поэтому при
 * прокрутке рисунок не скачет. Текст остаётся читаемым: затемнение небольшое.
 */
class DamagedBubbleDrawable(
    private val outgoing: Boolean,
    damage: Int,
    seed: Long,
    private val incomingColor: Int,
    private val outgoingColor: Int,
    private val density: Float
) : Drawable() {

    private val level = damage.coerceIn(0, 100) / 100f
    private val random = Random(seed)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val crack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#1F0B0714")
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#55FFFFFF")
    }
    private val ember = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFF7A18")
    }
    private val rect = RectF()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val dmg = level
        val base = if (outgoing) outgoingColor else incomingColor

        // Бабл как в релизе: та же заливка и скругления, без контура и тени.
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        if (outgoing) {
            fill.shader = android.graphics.LinearGradient(
                rect.left, rect.top, rect.right, rect.top,
                blend(outgoingColor, SOOT, 0.18f * dmg),
                blend(outgoingColor, SOOT, 0.30f * dmg),
                android.graphics.Shader.TileMode.CLAMP
            )
        } else {
            fill.shader = null
            fill.color = blend(incomingColor, SOOT, 0.24f * dmg)
        }
        canvas.drawPath(roundedPath(rect), fill)
        fill.shader = null

        // Стеклянные трещины: 1–4 точки удара с ветвлением.
        val impacts = 1 + (dmg * 2.4f).toInt().coerceAtMost(3)
        repeat(impacts) { drawImpactCracks(canvas, rect, dmg) }

        // Длинные разломы через бабл на сильных уровнях.
        if (dmg > 0.35f) {
            val fractures = when { dmg > 0.85f -> 2; dmg > 0.55f -> 1; else -> 0 }
            repeat(fractures) { drawFracture(canvas, rect, dmg) }
        }

        // Отломанные куски по кромке.
        val shards = (dmg * 3).toInt().coerceAtMost(3)
        repeat(shards) { drawShard(canvas, rect, dmg, base) }

        // Тлеющие угли вдоль трещин.
        if (dmg > 0.3f) {
            repeat(2 + (dmg * 9).toInt()) {
                val x = rect.left + random.nextFloat() * rect.width()
                val y = rect.top + random.nextFloat() * rect.height()
                ember.alpha = (90 + 150 * dmg).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, (0.6f + random.nextFloat() * 1.1f) * density, ember)
            }
        }
    }

    /** Точка удара: короткие лучи с ветками — как трещины на стекле. */
    private fun drawImpactCracks(canvas: Canvas, r: RectF, dmg: Float) {
        val cx = r.left + r.width() * (0.2f + random.nextFloat() * 0.6f)
        val cy = r.top + r.height() * (0.2f + random.nextFloat() * 0.6f)
        val rays = 3 + random.nextInt(3)
        val reach = (9f + 26f * random.nextFloat()) * density * (0.7f + dmg)
        crack.strokeWidth = (0.8f + 1.1f * dmg) * density
        crack.alpha = (120 + 110 * dmg).toInt().coerceIn(0, 255)
        highlight.strokeWidth = (0.5f + 0.6f * dmg) * density
        highlight.alpha = (40 + 70 * dmg).toInt().coerceIn(0, 255)

        repeat(rays) { ray ->
            var angle = (Math.PI * 2 / rays) * ray + random.nextFloat() * 0.5f
            var x = cx
            var y = cy
            path.reset()
            path.moveTo(x, y)
            val segments = 3 + random.nextInt(3)
            repeat(segments) {
                val step = reach / segments
                angle += (random.nextFloat() - 0.5f) * 0.9f
                x += kotlin.math.cos(angle).toFloat() * step
                y += kotlin.math.sin(angle).toFloat() * step
                path.lineTo(
                    x.coerceIn(r.left + 2, r.right - 2),
                    y.coerceIn(r.top + 2, r.bottom - 2)
                )
            }
            canvas.drawPath(path, crack)
            canvas.save()
            canvas.translate(0.8f * density, 0.8f * density)
            canvas.drawPath(path, highlight)
            canvas.restore()

            // Ветка от середины луча.
            if (random.nextFloat() < 0.7f) {
                val bx = (cx + x) / 2f
                val by = (cy + y) / 2f
                path.reset()
                path.moveTo(bx, by)
                var a2 = angle + (if (random.nextBoolean()) 1f else -1f) * (0.5f + random.nextFloat())
                var px = bx
                var py = by
                repeat(2) {
                    val step = (reach * 0.4f) / 2f
                    a2 += (random.nextFloat() - 0.5f) * 0.8f
                    px += kotlin.math.cos(a2).toFloat() * step
                    py += kotlin.math.sin(a2).toFloat() * step
                    path.lineTo(
                        px.coerceIn(r.left + 2, r.right - 2),
                        py.coerceIn(r.top + 2, r.bottom - 2)
                    )
                }
                canvas.drawPath(path, crack)
            }
        }
    }

    /** Длинный разлом через весь бабл. */
    private fun drawFracture(canvas: Canvas, r: RectF, dmg: Float) {
        val horizontal = random.nextBoolean()
        val startX: Float
        val startY: Float
        if (horizontal) {
            startX = r.left + 2
            startY = r.top + r.height() * (0.2f + random.nextFloat() * 0.6f)
        } else {
            startX = r.left + r.width() * (0.2f + random.nextFloat() * 0.6f)
            startY = r.top + 2
        }
        val endX = if (horizontal) r.right - 2 else r.left + r.width() * (0.1f + random.nextFloat() * 0.8f)
        val endY = if (horizontal) r.top + r.height() * (0.1f + random.nextFloat() * 0.8f) else r.bottom - 2
        val steps = 7
        var x = startX
        var y = startY
        path.reset()
        path.moveTo(x, y)
        repeat(steps) { i ->
            val t = (i + 1f) / steps
            x = startX + (endX - startX) * t + (random.nextFloat() - 0.5f) * 8f * density
            y = startY + (endY - startY) * t + (random.nextFloat() - 0.5f) * 8f * density
            path.lineTo(
                x.coerceIn(r.left + 2, r.right - 2),
                y.coerceIn(r.top + 2, r.bottom - 2)
            )
        }
        crack.strokeWidth = (1f + 1.4f * dmg) * density
        crack.alpha = (150 + 90 * dmg).toInt().coerceIn(0, 255)
        canvas.drawPath(path, crack)
        highlight.strokeWidth = 0.7f * density
        highlight.alpha = 70
        canvas.save()
        canvas.translate(1f * density, 1f * density)
        canvas.drawPath(path, highlight)
        canvas.restore()
    }

    /** Отломанный кусок: маленький осколок снаружи бабла, текст не перекрывает. */
    private fun drawShard(canvas: Canvas, r: RectF, dmg: Float, base: Int) {
        val side = random.nextInt(4)
        val size = (2.2f + 2.8f * random.nextFloat()) * density * (0.7f + 0.6f * dmg)
        val cx: Float
        val cy: Float
        when (side) {
            0 -> { cx = r.left + random.nextFloat() * r.width(); cy = r.top }
            1 -> { cx = r.left + random.nextFloat() * r.width(); cy = r.bottom }
            2 -> { cx = r.left; cy = r.top + random.nextFloat() * r.height() }
            else -> { cx = r.right; cy = r.top + random.nextFloat() * r.height() }
        }
        val dx = when (side) { 2 -> -1f; 3 -> 1f; else -> 0f }
        val dy = when (side) { 0 -> -1f; 1 -> 1f; else -> 0f }

        val shard = Path()
        // Осколок целиком снаружи кромки.
        shard.moveTo(cx + dx * size * 0.2f, cy + dy * size * 0.2f)
        shard.lineTo(cx + dx * size * 1.4f - dy * size * 0.7f, cy + dy * size * 1.4f - dx * size * 0.7f)
        shard.lineTo(cx + dx * size * 1.1f + dy * size * 0.9f, cy + dy * size * 1.1f + dx * size * 0.9f)
        shard.close()

        fill.color = blend(base, SOOT, 0.3f * dmg)
        canvas.drawPath(shard, fill)
        stroke.strokeWidth = 1.8f * density
        canvas.drawPath(shard, stroke)

        // Тёмная выемка ровно на кромке — след отлома, без заливки внутрь.
        stroke.strokeWidth = 2.2f * density
        stroke.color = Color.parseColor("#66000000")
        val notch = Path()
        notch.moveTo(cx - size * 0.9f, cy - size * 0.4f)
        notch.lineTo(cx + size * 0.9f, cy - size * 0.4f)
        canvas.drawPath(notch, stroke)
        stroke.color = Color.BLACK
    }

    private fun roundedPath(r: RectF): Path {
        // Ровно как bg_bubble_incoming/outgoing в релизной ветке.
        val radius = 16f * density
        val radii = floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
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
        const val SOOT = 0xFF120C20.toInt()
    }
}
