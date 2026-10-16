package com.example.muzea.data.mapper

import com.example.muzea.data.model.GifResponse
import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.data.model.KlipyMediaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GifMapperTest {

    private fun media(url: String, w: Int, h: Int) = KlipyMediaDto(url = url, dims = listOf(w, h))

    @Test
    fun `klipy gif maps gif and tinygif`() {
        val dto = KlipyGifDto(
            id = "slug-1",
            title = "cat",
            mediaFormats = mapOf(
                "gif" to media("https://c/gif.gif", 480, 270),
                "tinygif" to media("https://c/tiny.gif", 220, 124)
            )
        )

        val gif = dto.toGif()!!

        assertEquals("klipy", gif.provider)
        assertEquals("slug-1", gif.providerGifId)
        assertEquals("https://c/gif.gif", gif.url)
        assertEquals("https://c/tiny.gif", gif.previewUrl)
        assertEquals(480, gif.width)
        assertEquals(270, gif.height)
    }

    @Test
    fun `falls back to slug when id missing`() {
        val dto = KlipyGifDto(slug = "by-slug", mediaFormats = mapOf("gif" to media("u", 1, 1)))
        assertEquals("by-slug", dto.toGif()!!.providerGifId)
    }

    @Test
    fun `returns null without url or id`() {
        assertNull(KlipyGifDto(id = "x", mediaFormats = emptyMap()).toGif())
        assertNull(KlipyGifDto(mediaFormats = mapOf("gif" to media("u", 1, 1))).toGif())
    }

    @Test
    fun `api gif response maps to domain`() {
        val gif = GifResponse(
            provider = "klipy", providerGifId = "s", title = "t",
            url = "u", previewUrl = "p", width = 10, height = 20
        ).toGif()

        assertEquals("klipy", gif.provider)
        assertEquals("s", gif.providerGifId)
        assertEquals("p", gif.previewUrl)
        assertEquals(20, gif.height)
    }
}
