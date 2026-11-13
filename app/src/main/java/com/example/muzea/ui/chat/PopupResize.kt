package com.example.muzea.ui.chat

/**
 * Геометрия анимации раскрытия карточки: двигаем только ту границу, которая
 * дальше от сообщения, вторую оставляем на месте — иначе низ «дёргается».
 */
object PopupResize {

    /** Держать нижний край неподвижным (карточка растёт вверх). */
    fun keepBottom(targetY: Int, targetHeight: Int, startY: Int, startHeight: Int): Boolean {
        val bottomDelta = kotlin.math.abs(targetY + targetHeight - (startY + startHeight))
        val topDelta = kotlin.math.abs(targetY - startY)
        return bottomDelta <= topDelta
    }

    /** Y окна для промежуточной высоты, с прижатием к экрану. */
    fun frameY(
        startY: Int,
        startHeight: Int,
        height: Int,
        keepBottom: Boolean,
        screenHeight: Int,
        margin: Int
    ): Int {
        val raw = if (keepBottom) startY + startHeight - height else startY
        val maxY = (screenHeight - height - margin).coerceAtLeast(margin)
        return raw.coerceIn(margin, maxY)
    }
}
