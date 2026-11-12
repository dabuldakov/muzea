package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusListSizeTest {

    @Test
    fun `four rows fit without a limit`() {
        assertNull(StatusListSize.limitHeightPx(rows = 4, rowHeightPx = 84))
    }

    @Test
    fun `five rows are capped at four and a half`() {
        assertEquals(378, StatusListSize.limitHeightPx(rows = 5, rowHeightPx = 84))
    }

    @Test
    fun `large list keeps the same cap`() {
        assertEquals(378, StatusListSize.limitHeightPx(rows = 40, rowHeightPx = 84))
    }
}
