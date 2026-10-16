package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/**
 * Ответ Klipy. Схема близка к Tenor (`results` + `media_formats`), но поле с
 * результатами может называться и `data` — читаем оба варианта.
 */
data class KlipyGifsResponse(
    @SerializedName("results") val results: List<KlipyGifDto>? = null,
    @SerializedName("data") val data: List<KlipyGifDto>? = null
) {
    fun items(): List<KlipyGifDto> = results ?: data ?: emptyList()
}

data class KlipyGifDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content_description") val contentDescription: String? = null,
    @SerializedName("media_formats") val mediaFormats: Map<String, KlipyMediaDto>? = null,
    @SerializedName("media") val media: Map<String, KlipyMediaDto>? = null
) {
    fun formats(): Map<String, KlipyMediaDto> = mediaFormats ?: media ?: emptyMap()
}

data class KlipyMediaDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("dims") val dims: List<Int>? = null
)
