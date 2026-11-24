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

    @Test
    fun `level boundaries are clamped`() {
        assertEquals(0f, ChargeCalculator.level(-1))
        assertEquals(0f, ChargeCalculator.level(0))
        assertEquals(0f, ChargeCalculator.level(1_000))
        assertTrue(ChargeCalculator.level(1_001) > 0f)
        assertTrue(ChargeCalculator.level(59_999) < 1f)
        assertEquals(1f, ChargeCalculator.level(60_000))
        assertEquals(1f, ChargeCalculator.level(120_000))
        assertEquals(0.5f, ChargeCalculator.level(30_500))
    }

    @Test
    fun `power is monotonic and zero until charge starts`() {
        val samples = listOf(0L, 999L, 1_000L, 1_001L, 2_000L, 10_000L, 30_500L, 59_999L, 60_000L, 120_000L)
            .map(ChargeCalculator::power)
        assertEquals(0, samples.first())
        for (i in 1 until samples.size) {
            assertTrue("power must not drop at $i: $samples", samples[i] >= samples[i - 1])
        }
        assertEquals(100, samples.last())
    }

    @Test
    fun `charged threshold is inclusive`() {
        assertFalse(ChargeCalculator.isCharged(999))
        assertTrue(ChargeCalculator.isCharged(1_000))
        assertTrue(ChargeCalculator.isCharged(Long.MAX_VALUE))
    }
}
