package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionsPopupPositionTest {

    private fun pos(tapX: Int, tapY: Int) = ActionsPopupPosition.calculate(
        tapX = tapX,
        tapY = tapY,
        popupWidth = 300,
        popupHeight = 200,
        screenWidth = 1080,
        margin = 8
    )

    @Test
    fun `popup is centered on the tap and above it when there is space`() {
        val point = pos(tapX = 540, tapY = 700)
        assertEquals(390, point.x)
        assertEquals(492, point.y)
    }

    @Test
    fun `popup opens below the finger when the top is too close`() {
        val point = pos(tapX = 540, tapY = 150)
        assertEquals(158, point.y)
    }

    @Test
    fun `popup is clamped at screen edges`() {
        assertEquals(8, pos(tapX = 10, tapY = 700).x)
        assertEquals(772, pos(tapX = 1070, tapY = 700).x)
    }

    @Test
    fun `different taps produce different vertical positions`() {
        val first = pos(tapX = 500, tapY = 900)
        val second = pos(tapX = 500, tapY = 1300)
        assertEquals(350, first.x)
        assertEquals(692, first.y)
        assertEquals(1092, second.y)
    }
}
