package com.example.muzea.utils

import android.content.Context
import com.example.muzea.R
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CountFormatterTest {

    private val context = mockk<Context>()
    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        every { context.getString(R.string.count_thousand_short) } returns "K"
        every { context.getString(R.string.count_million_short) } returns "M"
        every { context.getString(R.string.count_billion_short) } returns "B"
        every {
            context.getString(R.string.count_compact_format, any<String>(), any<String>())
        } answers {
            val args = arg<Array<Any?>>(1)
            "${args[0]} ${args[1]}"
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `small values are shown as is`() {
        assertEquals("0", CountFormatter.format(context, 0))
        assertEquals("999", CountFormatter.format(context, 999))
    }

    @Test
    fun `thousands use short suffix`() {
        assertEquals("1.5 K", CountFormatter.format(context, 1_500))
        assertEquals("12 K", CountFormatter.format(context, 12_000))
    }

    @Test
    fun `millions and billions use short suffixes`() {
        assertEquals("2.5 M", CountFormatter.format(context, 2_500_000))
        assertEquals("3 B", CountFormatter.format(context, 3_000_000_000))
    }
}
