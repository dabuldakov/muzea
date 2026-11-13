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
 *
 * SimpleDateFormat не потокобезопасен и дорог в создании, поэтому держим по
 * экземпляру на поток: лента форматирует время на каждом биндинге.
 */
object LocalTimeFormatter {

    private const val SERVER_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    private const val OUT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm"
    private const val OUT_DATE_PATTERN = "yyyy-MM-dd"

    private val utcFormatter = ThreadLocal.withInitial {
        SimpleDateFormat(SERVER_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val localDateTimeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat(OUT_DATETIME_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    private val localDateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat(OUT_DATE_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    /** Полное время, например «2026-09-22 12:00» в поясе устройства. */
    fun format(iso: String?): String = format(iso, dateTime = true)

    /** Только дата, например «2026-09-22» в поясе устройства. */
    fun formatDate(iso: String?): String = format(iso, dateTime = false)

    /**
     * Время в том виде, в каком его ждёт сервер: UTC без зоны,
     * `2026-09-22T12:00:00`. [secondsAgo] сдвигает момент в прошлое.
     */
    fun serverTimestamp(secondsAgo: Long = 0): String {
        val millis = System.currentTimeMillis() - secondsAgo * 1000
        return utcFormatter.get()!!.format(Date(millis)).substring(0, 19)
    }

    private fun format(iso: String?, dateTime: Boolean): String {
        if (iso.isNullOrEmpty()) return ""
        return try {
            val date: Date = utcFormatter.get()!!.parse(iso.substring(0, 19))
            val out = (if (dateTime) localDateTimeFormatter.get() else localDateFormatter.get())!!
            // Пояс устройства мог смениться после создания форматтера.
            val zone = TimeZone.getDefault()
            if (out.timeZone != zone) out.timeZone = zone
            out.format(date)
        } catch (e: Exception) {
            iso
        }
    }
}
