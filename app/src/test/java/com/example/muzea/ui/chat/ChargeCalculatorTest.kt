package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeCalculatorTest {

    @Test
    fun `no charge before one second`() {
        assertFalse(ChargeCalculator.isCharged(0))
        assertFalse(ChargeCalculator.isCharged(999))
        assertEquals(0, ChargeCalculator.power(999))
    }

    @Test
    fun `charge starts at one second`() {
        assertTrue(ChargeCalculator.isCharged(1_000))
        assertEquals(0f, ChargeCalculator.level(1_000))
    }

    @Test
    fun `power grows non-linearly and caps at 100`() {
        val half = ChargeCalculator.power(30_500)
        assertEquals(71, half)
        assertEquals(100, ChargeCalculator.power(60_000))
        assertEquals(100, ChargeCalculator.power(120_000))
        assertTrue(ChargeCalculator.power(10_000) < ChargeCalculator.power(30_500))
    }
}
