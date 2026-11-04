package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Тело запроса на пересылку сообщения в другой чат. */
data class ForwardMessageRequest(
    @SerializedName("messageUuid")
    val messageUuid: String,
    @SerializedName("targetChatUuid")
    val targetChatUuid: String
)
