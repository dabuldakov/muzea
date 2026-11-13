package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupResizeTest {

    @Test
    fun `growing upwards keeps the bottom edge`() {
        // Карточка была 1000..1300, стала выше и её верх уехал до 700.
        assertTrue(PopupResize.keepBottom(targetY = 700, targetHeight = 600, startY = 1000, startHeight = 300))
    }

    @Test
    fun `growing downwards keeps the top edge`() {
        // Верх остался тем же, низ уехал вниз.
        assertFalse(PopupResize.keepBottom(targetY = 1000, targetHeight = 800, startY = 1000, startHeight = 300))
    }

    @Test
    fun `bottom anchored frame keeps the lower edge fixed`() {
        val y = PopupResize.frameY(
            startY = 1000,
            startHeight = 300,
            height = 600,
            keepBottom = true,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(700, y)
    }

    @Test
    fun `top anchored frame keeps the upper edge fixed`() {
        val y = PopupResize.frameY(
            startY = 1000,
            startHeight = 300,
            height = 600,
            keepBottom = false,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(1000, y)
    }

    @Test
    fun `frame does not go above the screen`() {
        val y = PopupResize.frameY(
            startY = 1000,
            startHeight = 300,
            height = 1400,
            keepBottom = true,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(8, y)
    }
}
