package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionsPopupWidthTest {

    @Test
    fun `width covers all action buttons`() {
        assertEquals(244, ActionsPopupWidth.calculate(6, 38, 8, 1080, 12))
    }

    @Test
    fun `width is capped by screen`() {
        assertEquals(480, ActionsPopupWidth.calculate(20, 38, 8, 504, 12))
    }

    @Test
    fun `width never smaller than paddings`() {
        assertEquals(16, ActionsPopupWidth.calculate(0, 38, 8, 1080, 12))
    }
}
