package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionOrderTest {

    @Test
    fun `recent reactions go first without duplicates`() {
        val result = ReactionOrder.ordered(
            recent = listOf("🔥", "👍"),
            full = listOf("👍", "🔥", "😂", "❤️")
        )
        assertEquals(listOf("🔥", "👍", "😂", "❤️"), result)
    }

    @Test
    fun `empty recent keeps the original order`() {
        assertEquals(
            listOf("👍", "❤️", "😂"),
            ReactionOrder.ordered(emptyList(), listOf("👍", "❤️", "😂"))
        )
    }
}
