package com.example.muzea.data.mapper

import com.example.muzea.data.model.GifResponse
import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.domain.model.Gif

/**
 * Klipy → домен. Для показа берём анимированный `gif` (hd → md → sm), превью —
 * маленький статичный `jpg`/`webp` (для сетки). Если ни одного URL нет или нет
 * идентификатора — гифка пропускается.
 */
fun KlipyGifDto.toGif(): Gif? {
    val sizes = file ?: return null

    val gif = sizes["hd"]?.get("gif")
        ?: sizes["md"]?.get("gif")
        ?: sizes["sm"]?.get("gif")
        ?: sizes.values.firstNotNullOfOrNull { it["gif"] }
    val url = gif?.url ?: return null

    val preview = sizes["sm"]?.get("jpg")?.url
        ?: sizes["sm"]?.get("webp")?.url
        ?: sizes["md"]?.get("jpg")?.url
        ?: sizes["md"]?.get("webp")?.url
        ?: url

    val identifier = slug ?: id?.toString() ?: return null

    return Gif(
        provider = "klipy",
        providerGifId = identifier,
        title = title,
        url = url,
        previewUrl = preview,
        width = gif.width,
        height = gif.height
    )
}

fun GifResponse.toGif(): Gif = Gif(
    provider = provider,
    providerGifId = providerGifId,
    title = title,
    url = url,
    previewUrl = previewUrl,
    width = width,
    height = height,
    gifKey = gifUuid
)
