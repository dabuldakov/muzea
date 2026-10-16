package com.example.muzea.data.mapper

import com.example.muzea.data.model.GifResponse
import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.data.model.KlipyMediaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GifMapperTest {

    private fun media(url: String, w: Int, h: Int) = KlipyMediaDto(url = url, width = w, height = h, size = 1)

    @Test
    fun `klipy gif maps animated gif and static preview`() {
        val dto = KlipyGifDto(
            id = 1L,
            slug = "slug-1",
            title = "cat",
            file = mapOf(
                "hd" to mapOf("gif" to media("https://c/hd.gif", 480, 270)),
                "md" to mapOf("gif" to media("https://c/md.gif", 220, 124)),
                "sm" to mapOf(
                    "jpg" to media("https://c/sm.jpg", 100, 60),
                    "webp" to media("https://c/sm.webp", 220, 124)
                )
            )
        )

        val gif = dto.toGif()!!

        assertEquals("klipy", gif.provider)
        assertEquals("slug-1", gif.providerGifId)
        assertEquals("https://c/hd.gif", gif.url)
        assertEquals("https://c/sm.jpg", gif.previewUrl)
        assertEquals(480, gif.width)
        assertEquals(270, gif.height)
    }

    @Test
    fun `falls back to id when slug missing`() {
        val dto = KlipyGifDto(
            id = 42L,
            file = mapOf("sm" to mapOf("gif" to media("u", 1, 1)))
        )
        assertEquals("42", dto.toGif()!!.providerGifId)
    }

    @Test
    fun `returns null without url or identifier`() {
        assertNull(KlipyGifDto(id = 1L, file = emptyMap()).toGif())
        assertNull(KlipyGifDto(file = mapOf("sm" to mapOf("gif" to media("u", 1, 1)))).toGif())
    }

    @Test
    fun `api gif response maps to domain with gif key`() {
        val gif = GifResponse(
            gifUuid = "key-1", provider = "klipy", providerGifId = "s", title = "t",
            url = "u", previewUrl = "p", width = 10, height = 20
        ).toGif()

        assertEquals("klipy", gif.provider)
        assertEquals("s", gif.providerGifId)
        assertEquals("p", gif.previewUrl)
        assertEquals("key-1", gif.gifKey)
        assertEquals(20, gif.height)
    }
}
