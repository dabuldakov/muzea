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
    fun `reaction action is not counted because the strip replaces it`() {
        val actions = listOf(
            MessageActionSpec("react", 0, 0),
            MessageActionSpec("copy", 0, 0),
            MessageActionSpec("reply", 0, 0),
            MessageActionSpec("select", 0, 0),
            MessageActionSpec("edit", 0, 0),
            MessageActionSpec("delete", 0, 0),
            MessageActionSpec("forward", 0, 0),
            MessageActionSpec("pin", 0, 0)
        )
        assertEquals(7, ActionsPopupWidth.visibleActionsCount(actions, "react"))
    }

    @Test
    fun `width by visible actions is narrower than by the whole list`() {
        val byAll = ActionsPopupWidth.calculate(8, 38, 8, 1080, 21)
        val byVisible = ActionsPopupWidth.calculate(7, 38, 8, 1080, 21)
        assertEquals(320, byAll)
        assertEquals(282, byVisible)
    }

    @Test
    fun `width never smaller than paddings`() {
        assertEquals(16, ActionsPopupWidth.calculate(0, 38, 8, 1080, 12))
    }
}
