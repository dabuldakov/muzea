package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTimeTest {

    @Test
    fun `iso timestamp becomes time only`() {
        assertEquals("12:34", StatusTime.format("2026-11-12T12:34:56"))
    }

    @Test
    fun `space separated timestamp becomes time only`() {
        assertEquals("17:47", StatusTime.format("2026-11-12 17:47:00"))
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", StatusTime.format(null))
        assertEquals("", StatusTime.format(""))
    }
}
