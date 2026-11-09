package com.example.muzea.ui.chat

/**
 * Расчёт «заряда» кнопки отправки.
 *
 * До [MIN_HOLD_MS] — обычная отправка, от неё до [MAX_HOLD_MS] сила растёт.
 * Визуально используем sqrt: иначе первые секунды почти не видны, а к концу
 * шкала прыгает.
 */
object ChargeCalculator {

    const val MIN_HOLD_MS = 1_000L
    const val MAX_HOLD_MS = 60_000L

    /** 0f..1f — линейная доля удержания. */
    fun level(holdMs: Long): Float {
        if (holdMs <= MIN_HOLD_MS) return 0f
        if (holdMs >= MAX_HOLD_MS) return 1f
        return (holdMs - MIN_HOLD_MS).toFloat() / (MAX_HOLD_MS - MIN_HOLD_MS)
    }

    /** 0..100 — сила для отправки и бейджа под сообщением. */
    fun power(holdMs: Long): Int = Math.round(kotlin.math.sqrt(level(holdMs)) * 100)

    fun isCharged(holdMs: Long): Boolean = holdMs >= MIN_HOLD_MS
}
