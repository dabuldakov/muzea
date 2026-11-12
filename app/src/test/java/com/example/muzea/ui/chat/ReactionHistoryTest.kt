package com.example.muzea.ui.chat

import android.content.Context
import com.example.muzea.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionHistoryTest {

    private val prefs = FakeSharedPreferences()

    private fun context(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    @Test
    fun `defaults are the first fifteen reactions`() {
        val recent = ReactionHistory.recent(context())
        assertEquals(15, recent.size)
        assertEquals(ReactionHistory.FULL.take(15), recent)
    }

    @Test
    fun `last used reaction goes first and is not duplicated`() {
        val context = context()
        ReactionHistory.record(context, "🔥")

        val recent = ReactionHistory.recent(context)
        assertEquals("🔥", recent.first())
        assertEquals(1, recent.count { it == "🔥" })
        assertEquals(15, recent.size)
    }

    @Test
    fun `history keeps at most fifteen unique reactions`() {
        val context = context()
        ReactionHistory.FULL.forEach { ReactionHistory.record(context, it) }

        val recent = ReactionHistory.recent(context)
        assertEquals(15, recent.size)
        assertEquals(15, recent.toSet().size)
    }

    @Test
    fun `history survives a new instance`() {
        val context = context()
        ReactionHistory.record(context, "🚀")

        assertTrue(ReactionHistory.recent(context).first() == "🚀")
    }
}
