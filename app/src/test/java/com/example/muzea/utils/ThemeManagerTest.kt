package com.example.muzea.utils

import android.content.Context
import com.example.muzea.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeManagerTest {

    private val prefs = FakeSharedPreferences()

    private fun context(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    @Test
    fun `standard is the default`() {
        val context = context()
        assertEquals(ThemeManager.STANDARD, ThemeManager.current(context))
        assertEquals(R.style.Theme_Muzea_Standard, ThemeManager.themeRes(context))
    }

    @Test
    fun `violet and pink map to their styles`() {
        val context = context()
        ThemeManager.set(context, ThemeManager.VIOLET)
        assertEquals(ThemeManager.VIOLET, ThemeManager.current(context))
        assertEquals(R.style.Theme_Muzea_Violet, ThemeManager.themeRes(context))

        ThemeManager.set(context, ThemeManager.PINK)
        assertEquals(R.style.Theme_Muzea_Pink, ThemeManager.themeRes(context))
    }
}
