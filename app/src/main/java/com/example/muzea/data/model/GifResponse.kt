package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Гифка из нашего API («Мои»/«Этот чат»). */
data class GifResponse(
    @SerializedName("gifUuid") val gifUuid: String? = null,
    @SerializedName("provider") val provider: String,
    @SerializedName("providerGifId") val providerGifId: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("mp4Url") val mp4Url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
)

/** Метаданные гифки, отправляемые при создании GIF-сообщения. */
data class GifRequest(
    @SerializedName("provider") val provider: String,
    @SerializedName("providerGifId") val providerGifId: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("mp4Url") val mp4Url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
)
