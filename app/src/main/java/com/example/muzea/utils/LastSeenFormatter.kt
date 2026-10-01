package com.example.muzea.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Разбирает строку `lastSeenAt`, присланную сервером, в момент времени.
 *
 * Сервер отдаёт LocalDateTime в UTC: либо с суффиксом `Z`, либо без зоны
 * (как хранится в БД). Строка без зоны трактуется как UTC — так же, как в
 * [LocalTimeFormatter].
 */
object LastSeenFormatter {

    private const val SERVER_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

    /**
     * Момент последней активности либо null, если данных нет.
     *
     * null и «очень давно» — разные вещи: null значит, что пользователь ещё
     * ни разу не заходил, и показывать «был(а) N назад» здесь нельзя.
     */
    fun parse(iso: String?): Date? {
        if (iso.isNullOrEmpty()) return null
        return try {
            SimpleDateFormat(SERVER_PATTERN, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso.substring(0, 19))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Возраст в целых минутах, округлённых вниз. Отрицательные значения
     * (серверное время впереди клиентского из-за рассинхрона часов)
     * схлопываются в ноль, чтобы не показывать «через -2 мин».
     */
    fun minutesAgo(iso: String?, now: Long = System.currentTimeMillis()): Long? {
        val seenAt = parse(iso) ?: return null
        val diffMillis = now - seenAt.time
        if (diffMillis <= 0L) return 0L
        return diffMillis / 60_000L
    }
}