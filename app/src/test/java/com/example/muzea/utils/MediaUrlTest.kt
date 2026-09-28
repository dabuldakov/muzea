package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlTest {

    companion object {
        private const val MAIN = "https://api-muzea.su"
        private const val CHAT = "https://chat-muzea.su"
    }

    @Test
    fun `absolute http url is returned unchanged`() {
        val url = "https://api-muzea.su/api/news/image/abc.jpeg"
        assertEquals(url, MediaUrl.absolute(MAIN, url))
    }

    @Test
    fun `absolute https url is returned unchanged`() {
        val url = "https://cdn.example.com/pic.png"
        assertEquals(url, MediaUrl.absolute(MAIN, url))
    }

    @Test
    fun `absolute image url must not be prefixed with base (regression taken from live server)`() {
        val serverUrl = "https://api-muzea.su/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg"
        assertEquals(serverUrl, MediaUrl.main(serverUrl))
    }

    @Test
    fun `relative path with leading slash is joined to base`() {
        assertEquals(
            "https://api-muzea.su/api/videos/stream/file.mp4",
            MediaUrl.absolute(MAIN, "/api/videos/stream/file.mp4")
        )
    }

    @Test
    fun `relative path without leading slash is joined to base`() {
        assertEquals(
            "https://chat-muzea.su/api/avatars/uuid.png",
            MediaUrl.chat("api/avatars/uuid.png")
        )
    }

    @Test
    fun `base url trailing slash does not double it`() {
        assertEquals(
            "https://chat-muzea.su/api/avatars/uuid.png",
            MediaUrl.absolute("https://chat-muzea.su/", "/api/avatars/uuid.png")
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "https://api-muzea.su/a.png",
            MediaUrl.absolute(MAIN, "  /a.png  ")
        )
    }

    @Test
    fun `blank or null path returns null`() {
        assertNull(MediaUrl.absolute(MAIN, null))
        assertNull(MediaUrl.absolute(MAIN, ""))
        assertNull(MediaUrl.absolute(MAIN, "   "))
        assertNull(MediaUrl.main(null))
        assertNull(MediaUrl.chat("  "))
    }
}