package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class SendMessageRequest(
    @SerializedName("text")
    val text: String = "",
    @SerializedName("messageType")
    val messageType: String = "TEXT",
    @SerializedName("replyToMessageUuid")
    val replyToMessageUuid: String? = null,
    @SerializedName("gif")
    val gif: GifRequest? = null,
    @SerializedName("attachments")
    val attachments: List<String>? = null,
    /** Клиентский UUID: повторная отправка не создаёт дубль. */
    @SerializedName("clientMessageId")
    val clientMessageId: String? = null
)