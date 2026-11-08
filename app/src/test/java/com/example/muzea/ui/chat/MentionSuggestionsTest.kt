package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionSuggestionsTest {

    private val names = listOf("Morris", "dabuldakov", "opencode", "Morris")

    @Test
    fun `at chat start offers every participant without duplicates`() {
        val query = MentionSuggestions.resolve("@", 1, names)

        assertEquals(0, query?.start)
        assertEquals(1, query?.end)
        assertEquals(listOf("Morris", "dabuldakov", "opencode"), query?.candidates)
    }

    @Test
    fun `query filters case-insensitively`() {
        assertEquals(
            listOf("opencode"),
            MentionSuggestions.resolve("привет @Ope", 11, names)?.candidates
        )
    }

    @Test
    fun `mention after word is not suggested`() {
        assertNull(MentionSuggestions.resolve("mail@mor", 9, names))
    }

    @Test
    fun `space after at closes suggestions`() {
        assertNull(MentionSuggestions.resolve("@Morris hi", 10, names))
    }

    @Test
    fun `no match gives no popup`() {
        assertNull(MentionSuggestions.resolve("@zzz", 4, names))
    }

    @Test
    fun `respects candidate limit`() {
        assertEquals(2, MentionSuggestions.resolve("@", 1, names, limit = 2)?.candidates?.size)
    }

    @Test
    fun `cursor outside text is ignored`() {
        assertNull(MentionSuggestions.resolve("@Morris", 0, names))
        assertNull(MentionSuggestions.resolve("@Morris", 20, names))
    }
}
