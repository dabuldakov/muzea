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
    fun `klipy prefers animated webp and uses static preview`() {
        val dto = KlipyGifDto(
            id = 1L,
            slug = "slug-1",
            title = "cat",
            file = mapOf(
                "hd" to mapOf(
                    "gif" to media("https://c/hd.gif", 480, 270),
                    "webp" to media("https://c/hd.webp", 480, 270),
                    "mp4" to media("https://c/hd.mp4", 480, 270)
                ),
                "sm" to mapOf(
                    "jpg" to media("https://c/sm.jpg", 100, 60),
                    "webp" to media("https://c/sm.webp", 220, 124)
                )
            )
        )

        val gif = dto.toGif()!!

        assertEquals("klipy", gif.provider)
        assertEquals("slug-1", gif.providerGifId)
        assertEquals("https://c/hd.webp", gif.url)
        assertEquals("https://c/sm.jpg", gif.previewUrl)
        assertEquals("https://c/hd.mp4", gif.mp4Url)
        assertEquals(480, gif.width)
        assertEquals(270, gif.height)
    }

    @Test
    fun `klipy falls back to gif when no webp`() {
        val dto = KlipyGifDto(
            id = 2L,
            file = mapOf("sm" to mapOf("gif" to media("https://c/only.gif", 200, 100)))
        )
        assertEquals("https://c/only.gif", dto.toGif()!!.url)
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
        // Относительный путь резолвится в абсолютный адрес чата.
        assertEquals("https://chat-muzea.su/p", gif.previewUrl)
        assertEquals("https://chat-muzea.su/u", gif.url)
        assertEquals("key-1", gif.gifKey)
        assertEquals(20, gif.height)
    }
}
