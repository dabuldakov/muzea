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
    fun `expanded grid skips reactions that are already in the strip`() {
        val result = ReactionOrder.rest(
            recent = listOf("🔥", "👍"),
            full = listOf("👍", "❤️", "🔥", "😂")
        )
        assertEquals(listOf("❤️", "😂"), result)
    }

    @Test
    fun `expanded grid is empty when the strip holds every reaction`() {
        assertEquals(
            emptyList<String>(),
            ReactionOrder.rest(listOf("👍", "❤️"), listOf("👍", "❤️"))
        )
    }

    @Test
    fun `empty recent keeps the original order`() {
        assertEquals(
            listOf("👍", "❤️", "😂"),
            ReactionOrder.ordered(emptyList(), listOf("👍", "❤️", "😂"))
        )
    }
}
