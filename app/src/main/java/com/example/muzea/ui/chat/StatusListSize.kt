package com.example.muzea.ui.chat

/**
 * Высота раскрытого списка статусов: показываем четыре с половиной строки,
 * чтобы половинка пятой подсказывала прокрутку.
 */
object StatusListSize {

    const val VISIBLE_ROWS = 4.5f

    /** Ограничение по высоте в пикселях или null, если все строки влезают. */
    fun limitHeightPx(rows: Int, rowHeightPx: Int): Int? =
        if (rows > VISIBLE_ROWS.toInt()) (rowHeightPx * VISIBLE_ROWS).toInt() else null
}
