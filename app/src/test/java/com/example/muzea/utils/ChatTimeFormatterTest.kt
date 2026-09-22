package com.example.muzea.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ChatTimeFormatterTest {

    private val originalTimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `utc time is converted to device local time`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))

        // Сервер (UTC) пишет 12:00 -> в Москве (UTC+3) должно быть 15:00
        assertEquals("2026-09-22 15:00", ChatTimeFormatter.format("2026-09-22T12:00:00"))
    }

    @Test
    fun `nanosecond precision from server is handled`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("2026-09-22 12:00", ChatTimeFormatter.format("2026-09-22T12:00:00.123456789"))
    }

    @Test
    fun `same utc time stays unchanged in utc zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("2026-09-22 12:00", ChatTimeFormatter.format("2026-09-22T12:00:00"))
    }

    @Test
    fun `empty or null returns empty string`() {
        assertEquals("", ChatTimeFormatter.format(null))
        assertEquals("", ChatTimeFormatter.format(""))
    }

    @Test
    fun `malformed input is returned as is`() {
        assertEquals("not-a-date", ChatTimeFormatter.format("not-a-date"))
    }
}