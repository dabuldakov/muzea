package com.example.muzea.data.mapper

import com.example.muzea.data.model.GifResponse
import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.domain.model.Gif
import com.example.muzea.utils.MediaUrl

/**
 * Klipy → домен. Для показа берём анимированный `gif` (hd → md → sm), превью —
 * маленький статичный `jpg`/`webp` (для сетки). Если ни одного URL нет или нет
 * идентификатора — гифка пропускается.
 */
fun KlipyGifDto.toGif(): Gif? {
    val sizes = file ?: return null

    // Анимированный WebP предпочитаем GIF: у Klipy те же размеры, но полный цвет,
    // файл в разы меньше и воспроизведение плавнее. GIF — запасной вариант.
    val animated = sizes["hd"]?.get("webp")
        ?: sizes["md"]?.get("webp")
        ?: sizes["sm"]?.get("webp")
        ?: sizes["hd"]?.get("gif")
        ?: sizes["md"]?.get("gif")
        ?: sizes["sm"]?.get("gif")
        ?: sizes.values.firstNotNullOfOrNull { it["webp"] ?: it["gif"] }
    val url = animated?.url ?: return null

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
        width = animated.width,
        height = animated.height
    )
}

fun GifResponse.toGif(): Gif = Gif(
    provider = provider,
    providerGifId = providerGifId,
    title = title,
    // Наш сервер отдаёт относительные URL (/api/gifs/file/…), поэтому резолвим
    // их в абсолютный адрес чата. Абсолютные (Klipy) остаются как есть.
    url = MediaUrl.chat(url) ?: url,
    previewUrl = MediaUrl.chat(previewUrl),
    width = width,
    height = height,
    gifKey = gifUuid
)
