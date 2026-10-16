package com.example.muzea.data.mapper

import com.example.muzea.data.model.GifResponse
import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.domain.model.Gif

/**
 * Klipy → домен. Формат берём `gif` (при отсутствии — самый крупный из доступных),
 * превью — `tinygif`/`nanogif`. Если у формата нет URL или идентификатора, гифка
 * пропускается.
 */
fun KlipyGifDto.toGif(): Gif? {
    val formats = formats()
    val gif = formats["gif"] ?: formats["mediumgif"] ?: formats.values.firstOrNull()
    val url = gif?.url ?: return null
    val preview = (formats["tinygif"] ?: formats["nanogif"] ?: formats["tinywebp"])?.url ?: url
    val dims = gif.dims
    val identifier = id ?: slug ?: return null
    return Gif(
        provider = "klipy",
        providerGifId = identifier,
        title = title ?: contentDescription,
        url = url,
        previewUrl = preview,
        width = dims?.getOrNull(0),
        height = dims?.getOrNull(1)
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
