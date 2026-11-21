package com.example.muzea.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResendCooldownTest {

    @Test
    fun `cooldown counts down to zero`() {
        val cooldown = ResendCooldown(totalSeconds = 2)

        assertFalse(cooldown.active)

        cooldown.start()
        assertTrue(cooldown.active)
        assertEquals(2, cooldown.seconds)

        cooldown.tick()
        assertTrue(cooldown.active)
        assertEquals(1, cooldown.seconds)

        cooldown.tick()
        assertFalse(cooldown.active)
        assertEquals(0, cooldown.seconds)

        // Ниже нуля не уходим.
        cooldown.tick()
        assertEquals(0, cooldown.seconds)
    }
}
