package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Контракт прямой presigned-загрузки вложений чата. */
data class PresignAttachmentRequest(
    @SerializedName("chatUuid") val chatUuid: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("contentType") val contentType: String?,
    @SerializedName("size") val size: Long,
    /** ATTACHMENT или THUMBNAIL. */
    @SerializedName("kind") val kind: String
)

data class PresignAttachmentResponse(
    @SerializedName("uploadUrl") val uploadUrl: String,
    @SerializedName("objectKey") val objectKey: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int
)

data class FinalizeAttachmentRequest(
    @SerializedName("chatUuid") val chatUuid: String,
    @SerializedName("objectKey") val objectKey: String,
    @SerializedName("thumbnailKey") val thumbnailKey: String?,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("duration") val duration: Int?
)
