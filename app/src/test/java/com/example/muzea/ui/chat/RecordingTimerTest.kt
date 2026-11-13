package com.example.muzea.ui.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingTimerTest {

    @Test
    fun `ticks and stops at the limit`() = runTest {
        val ticks = mutableListOf<String>()
        var limits = 0
        val timer = RecordingTimer(
            scope = this,
            now = { testScheduler.currentTime },
            format = { ms -> "${ms / 1000}s" },
            onTick = { ticks.add(it) },
            onLimit = { limits++ }
        )

        timer.start(maxMs = 500)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.contains("0s"))
        assertEquals(1, limits)
    }

    @Test
    fun `stop prevents the limit callback`() = runTest {
        var limits = 0
        val timer = RecordingTimer(
            scope = this,
            now = { testScheduler.currentTime },
            format = { "$it" },
            onTick = {},
            onLimit = { limits++ }
        )

        timer.start(maxMs = 500)
        advanceTimeBy(300)
        timer.stop()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(0, limits)
    }
}
