package com.example.muzea.utils

import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class LastSeenFormatterTest {

    /** Момент `minutesAgo` минут назад, в виде строки, которую шлёт сервер. */
    private fun isoMinutesAgo(minutesAgo: Long, now: Long = System.currentTimeMillis()): String {
        val millis = now - TimeUnit.MINUTES.toMillis(minutesAgo)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis
        }
        val pattern = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        return pattern.format(calendar.time)
    }

    @Test
    fun `minutesAgo counts full minutes`() {
        val now = 1_800_000_000_000L

        assertEquals(0L, LastSeenFormatter.minutesAgo("2027-01-15T08:00:00", now))
        assertEquals(5L, LastSeenFormatter.minutesAgo(isoMinutesAgo(5, now), now))
        assertEquals(120L, LastSeenFormatter.minutesAgo(isoMinutesAgo(120, now), now))
    }

    @Test
    fun `minutesAgo returns null when user was never online`() {
        // null и «давно не был» — разные вещи: первый случай не должен
        // превращаться в «был(а) 99999999 мин назад».
        assertNull(LastSeenFormatter.minutesAgo(null, System.currentTimeMillis()))
        assertNull(LastSeenFormatter.minutesAgo("", System.currentTimeMillis()))
    }

    @Test
    fun `minutesAgo clamps future timestamps to zero`() {
        // Рассинхрон часов устройства с сервером не должен давать отрицательный возраст.
        val now = 1_800_000_000_000L

        assertEquals(0L, LastSeenFormatter.minutesAgo(isoMinutesAgo(-30, now), now))
    }

    @Test
    fun `minutesAgo returns null for unparsable string`() {
        assertNull(LastSeenFormatter.minutesAgo("не дата", System.currentTimeMillis()))
        assertNull(LastSeenFormatter.parse("не дата"))
    }

    @Test
    fun `parse accepts string with z suffix`() {
        // Строка без зоны и с суффиксом Z должны означать один и тот же момент:
        // сервер шлёт оба варианта в зависимости от настройки сериализации.
        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 22, 12, 0, 0)
        }.timeInMillis

        assertEquals(expected, LastSeenFormatter.parse("2026-09-22T12:00:00")!!.time)
        assertEquals(expected, LastSeenFormatter.parse("2026-09-22T12:00:00Z")!!.time)
    }

    @Test
    fun `parse tolerates fractional seconds`() {
        assertNotNull(LastSeenFormatter.parse("2026-09-22T12:00:00.123456Z"))
    }
}