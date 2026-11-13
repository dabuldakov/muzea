package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupResizeTest {

    @Test
    fun `card above the finger grows upwards`() {
        // Карточка 1000..1300, палец ниже — растим вверх, низ стоит.
        assertTrue(PopupResize.growsUp(popupY = 1000, popupHeight = 300, tapY = 1500))
    }

    @Test
    fun `card under the finger grows downwards`() {
        assertFalse(PopupResize.growsUp(popupY = 1600, popupHeight = 300, tapY = 1500))
    }

    @Test
    fun `growing up keeps the bottom edge fixed`() {
        val y = PopupResize.yForHeight(
            growsUp = true,
            edgeAnchor = 1300,
            height = 600,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(700, y)
    }

    @Test
    fun `growing down keeps the top edge fixed`() {
        val y = PopupResize.yForHeight(
            growsUp = false,
            edgeAnchor = 1000,
            height = 600,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(1000, y)
    }

    @Test
    fun `card does not go above the screen`() {
        val y = PopupResize.yForHeight(
            growsUp = true,
            edgeAnchor = 1300,
            height = 1400,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(8, y)
    }

    @Test
    fun `card does not go below the screen`() {
        val y = PopupResize.yForHeight(
            growsUp = false,
            edgeAnchor = 2000,
            height = 600,
            screenHeight = 2400,
            margin = 8
        )
        assertEquals(1792, y)
    }
}
