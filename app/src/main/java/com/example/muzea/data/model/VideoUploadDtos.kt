package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/**
 * Контракт прямой загрузки видео в MinIO (presigned PUT).
 *
 * Backend выдаёт временный URL, клиент льёт файл напрямую в объектное
 * хранилище, затем сообщает objectKey для создания записи видео.
 */
data class PresignUploadRequest(
    @SerializedName("contentType")
    val contentType: String,
    @SerializedName("size")
    val size: Long
)

data class PresignUploadResponse(
    @SerializedName("uploadUrl")
    val uploadUrl: String,
    @SerializedName("objectKey")
    val objectKey: String,
    @SerializedName("expiresInSeconds")
    val expiresInSeconds: Int
)

data class FinalizeVideoUploadRequest(
    @SerializedName("objectKey")
    val objectKey: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String?
)
