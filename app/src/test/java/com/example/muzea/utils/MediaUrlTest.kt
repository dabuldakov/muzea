package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlTest {

    companion object {
        private const val MAIN = "http://90.188.89.63:8085"
        private const val CHAT = "http://90.188.89.63:8086"
    }

    @Test
    fun `absolute http url is returned unchanged`() {
        val url = "http://90.188.89.63:8085/api/news/image/abc.jpeg"
        assertEquals(url, MediaUrl.absolute(MAIN, url))
    }

    @Test
    fun `absolute https url is returned unchanged`() {
        val url = "https://cdn.example.com/pic.png"
        assertEquals(url, MediaUrl.absolute(MAIN, url))
    }

    @Test
    fun `absolute image url must not be prefixed with base (regression taken from live server)`() {
        val serverUrl = "http://90.188.89.63:8085/api/news/image/4b1d5b51-07ac-4f75-b400-84551dae5a38.jpeg"
        assertEquals(serverUrl, MediaUrl.main(serverUrl))
    }

    @Test
    fun `relative path with leading slash is joined to base`() {
        assertEquals(
            "http://90.188.89.63:8085/api/videos/stream/file.mp4",
            MediaUrl.absolute(MAIN, "/api/videos/stream/file.mp4")
        )
    }

    @Test
    fun `relative path without leading slash is joined to base`() {
        assertEquals(
            "http://90.188.89.63:8086/api/avatars/uuid.png",
            MediaUrl.chat("api/avatars/uuid.png")
        )
    }

    @Test
    fun `base url trailing slash does not double it`() {
        assertEquals(
            "http://90.188.89.63:8086/api/avatars/uuid.png",
            MediaUrl.absolute("http://90.188.89.63:8086/", "/api/avatars/uuid.png")
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "http://90.188.89.63:8085/a.png",
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