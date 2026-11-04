package com.example.muzea.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNUSED_EXPRESSION")
class KlipyModelsTest {

    private fun <T> assertDataClassContract(first: T, second: T, different: T) {
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
        assertFalse(first == null)
        assertTrue(first.toString().isNotEmpty())
    }

    @Test
    fun `media dto data class contract`() {
        val first = KlipyMediaDto("url", 10, 20, 100L)
        val different = first.copy(url = "other")

        assertDataClassContract(first, KlipyMediaDto("url", 10, 20, 100L), different)
        first.component1(); first.component2(); first.component3(); first.component4()
    }

    @Test
    fun `gif dto data class contract`() {
        val file = mapOf("hd" to mapOf("gif" to KlipyMediaDto("u", 1, 1, 1L)))
        val first = KlipyGifDto(1L, "slug", "title", file)
        val different = first.copy(title = "other")

        assertDataClassContract(first, KlipyGifDto(1L, "slug", "title", file), different)
        first.component1(); first.component2(); first.component3(); first.component4()
    }

    @Test
    fun `page data class contract`() {
        val item = KlipyGifDto(1L, "slug", "title", null)
        val first = KlipyPage(listOf(item), 1, 10, true)
        val different = first.copy(hasNext = false)

        assertDataClassContract(first, KlipyPage(listOf(item), 1, 10, true), different)
        first.component1(); first.component2(); first.component3(); first.component4()
    }

    @Test
    fun `gifs response data class contract`() {
        val page = KlipyPage(listOf(KlipyGifDto(1L, "slug", "title", null)), 1, 10, false)
        val first = KlipyGifsResponse(true, page)
        val different = first.copy(result = false)

        assertDataClassContract(first, KlipyGifsResponse(true, page), different)
        first.component1(); first.component2()
    }

    @Test
    fun `items returns page items when present`() {
        val item = KlipyGifDto(1L, "slug", "title", null)
        val response = KlipyGifsResponse(true, KlipyPage(listOf(item), 1, 10, false))

        assertEquals(listOf(item), response.items())
    }

    @Test
    fun `items returns empty list when page is missing`() {
        assertTrue(KlipyGifsResponse(null, null).items().isEmpty())
        assertTrue(KlipyGifsResponse(true, KlipyPage(null, null, null, null)).items().isEmpty())
    }
}
