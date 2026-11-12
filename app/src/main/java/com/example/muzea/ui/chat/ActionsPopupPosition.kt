package com.example.muzea.ui.chat

/**
 * Позиция плавающей модалки относительно точки касания.
 *
 * Чистая функция: прижимаем по горизонтали к экрану, а по вертикали
 * показываем над пальцем, если места хватает, иначе — под ним.
 */
object ActionsPopupPosition {

    data class Point(val x: Int, val y: Int)

    fun calculate(
        tapX: Int,
        tapY: Int,
        popupWidth: Int,
        popupHeight: Int,
        screenWidth: Int,
        margin: Int
    ): Point {
        val maxX = (screenWidth - popupWidth - margin).coerceAtLeast(margin)
        val x = (tapX - popupWidth / 2).coerceIn(margin, maxX)
        val above = tapY - popupHeight - margin
        val y = if (above >= margin) above else tapY + margin
        return Point(x, y)
    }
}
