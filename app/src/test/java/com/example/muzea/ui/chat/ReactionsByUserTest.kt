package com.example.muzea.ui.chat

import com.example.muzea.domain.model.MessageReaction
import com.example.muzea.domain.model.ReactionUser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionsByUserTest {

    private fun reaction(emoji: String, vararg users: Pair<String, String>) = MessageReaction(
        emoji = emoji,
        count = users.size,
        mine = false,
        users = users.map { ReactionUser(it.first, it.second) }
    )

    @Test
    fun `reactions are grouped by author and ordered by time`() {
        val reactions = listOf(
            reaction("👍", "u2" to "2026-11-12T10:05", "u1" to "2026-11-12T10:01"),
            reaction("❤️", "u1" to "2026-11-12T10:03")
        )

        val byUser = ReactionsByUser.map(reactions)

        assertEquals(listOf("👍", "❤️"), byUser["u1"])
        assertEquals(listOf("👍"), byUser["u2"])
    }

    @Test
    fun `user without reactions is absent`() {
        val byUser = ReactionsByUser.map(listOf(reaction("👍", "u1" to "2026-11-12T10:00")))
        assertEquals(null, byUser["u2"])
    }

    @Test
    fun `first three icons are shown and the rest become a counter`() {
        val (shown, hidden) = ReactionsByUser.visible(listOf("👍", "❤️", "😂", "🔥", "🎉"))
        assertEquals(listOf("👍", "❤️", "😂"), shown)
        assertEquals(2, hidden)
    }

    @Test
    fun `three or fewer have no counter`() {
        assertEquals(listOf("👍", "❤️", "😂") to 0,
            ReactionsByUser.visible(listOf("👍", "❤️", "😂")))
        assertEquals(listOf("👍") to 0, ReactionsByUser.visible(listOf("👍")))
    }
}
