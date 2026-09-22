package com.example.muzea.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class LocalTimeFormatterTest {

    private val originalTimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `formatDate shows local date`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        // 2026-09-22 12:00 UTC -> в Москве это уже 2026-09-22 15:00, дата та же
        assertEquals("2026-09-22", LocalTimeFormatter.formatDate("2026-09-22T12:00:00"))
    }

    @Test
    fun `formatDate rolls to previous date across midnight in local zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        // 2026-09-21 21:00 UTC -> 22:00? нет, +3 => 2026-09-22 00:00 в Москве
        // Возьмём 0:30 UTC 22-го -> в Москве 3:30, дата та же
        assertEquals("2026-09-22", LocalTimeFormatter.formatDate("2026-09-22T00:30:00"))
    }

    @Test
    fun `formatDate accepts string with z suffix`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("2026-09-22", LocalTimeFormatter.formatDate("2026-09-22T12:00:00Z"))
    }

    @Test
    fun `format full datetime in local zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        assertEquals("2026-09-22 15:00", LocalTimeFormatter.format("2026-09-22T12:00:00"))
    }
}