package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Жалоба на сообщение/пользователя/чат (chat-muzea). */
data class ChatReportRequest(
    @SerializedName("contentType") val contentType: String,
    @SerializedName("contentUuid") val contentUuid: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("comment") val comment: String? = null
)

/** Жалоба на новость или видео (api-muzea). */
data class ContentReportRequest(
    @SerializedName("contentType") val contentType: String,
    @SerializedName("contentId") val contentId: Long,
    @SerializedName("reason") val reason: String,
    @SerializedName("comment") val comment: String? = null
)

/** Блокировка пользователя (chat-muzea). */
data class BlockUserRequest(
    @SerializedName("blockUserUuid") val blockUserUuid: String,
    @SerializedName("reason") val reason: String? = null
)
