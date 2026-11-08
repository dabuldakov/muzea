package com.example.muzea.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Контракты DTO, которые не проверялись напрямую: страницы и участники. */
class ResponseModelsTest {

    @Test
    fun `page response exposes Spring page fields`() {
        val page = PageResponse<String>(
            content = listOf("a", "b"),
            empty = false,
            first = true,
            last = false,
            number = 0,
            numberOfElements = 2,
            size = 20,
            totalElements = 42,
            totalPages = 3
        )

        assertEquals(listOf("a", "b"), page.content)
        assertFalse(page.empty)
        assertTrue(page.first)
        assertFalse(page.last)
        assertEquals(0, page.number)
        assertEquals(2, page.numberOfElements)
        assertEquals(20, page.size)
        assertEquals(42, page.totalElements)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun `chat user response keeps online flag and nullable names`() {
        val user = ChatUserResponse(
            userUuid = "u-1",
            username = "morris",
            firstName = null,
            lastName = null,
            fullName = null,
            avatarUrl = null,
            isOnline = true
        )

        assertEquals("u-1", user.userUuid)
        assertEquals("morris", user.username)
        assertTrue(user.isOnline)
    }
}
