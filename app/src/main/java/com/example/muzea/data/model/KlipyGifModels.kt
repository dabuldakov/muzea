package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/**
 * Ответ Klipy GIF API.
 * `{ "result": true, "data": { "data": [ {id, slug, title, file{hd{...},md{...},sm{...}}} ],
 *   "current_page", "per_page", "has_next" } }`.
 */
data class KlipyGifsResponse(
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("data") val data: KlipyPage? = null
) {
    fun items(): List<KlipyGifDto> = data?.items.orEmpty()
}

data class KlipyPage(
    @SerializedName("data") val items: List<KlipyGifDto>? = null,
    @SerializedName("current_page") val currentPage: Int? = null,
    @SerializedName("per_page") val perPage: Int? = null,
    @SerializedName("has_next") val hasNext: Boolean? = null
)

data class KlipyGifDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("title") val title: String? = null,
    /** Размер → формат (gif/webp/jpg/mp4/…) → медиа. */
    @SerializedName("file") val file: Map<String, Map<String, KlipyMediaDto>>? = null
)

data class KlipyMediaDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("size") val size: Long? = null
)
