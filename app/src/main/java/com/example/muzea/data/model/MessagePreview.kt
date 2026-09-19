package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class MessagePreview(
    @SerializedName("messageUuid")
    val messageUuid: String?,
    @SerializedName("text")
    val text: String?,
    @SerializedName("senderId")
    val senderId: Long?,
    @SerializedName("senderName")
    val senderName: String?,
    @SerializedName("createdAt")
    val createdAt: String?
)