package com.example.muzea.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Форматирует время, присланное сервером, в локальный часовой пояс устройства.
 *
 * Серверы хранят дату/время как LocalDateTime без смещения (таймзона контейнера = UTC,
 * а время пишется в UTC), поэтому строку без зоны трактуем как UTC.
 */
object LocalTimeFormatter {

    private const val SERVER_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    private const val OUT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm"
    private const val OUT_DATE_PATTERN = "yyyy-MM-dd"

    /** Полное время, например «2026-09-22 12:00» в поясе устройства. */
    fun format(iso: String?): String = format(iso, OUT_DATETIME_PATTERN)

    /** Только дата, например «2026-09-22» в поясе устройства. */
    fun formatDate(iso: String?): String = format(iso, OUT_DATE_PATTERN)

    private fun format(iso: String?, outPattern: String): String {
        if (iso.isNullOrEmpty()) return ""
        return try {
            val utcFormat = SimpleDateFormat(SERVER_PATTERN, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val localFormat = SimpleDateFormat(outPattern, Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val date: Date = utcFormat.parse(iso.substring(0, 19))
            localFormat.format(date)
        } catch (e: Exception) {
            iso
        }
    }
}