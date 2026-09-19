package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatResponse(
    @SerializedName("chatUuid")
    val chatUuid: String,
    @SerializedName("chatType")
    val chatType: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    @SerializedName("createdAt")
    val createdAt: String?,
    @SerializedName("updatedAt")
    val updatedAt: String?,
    @SerializedName("participantCount")
    val participantCount: Long?,
    @SerializedName("lastMessage")
    val lastMessage: MessagePreview?,
    @SerializedName("unreadCount")
    val unreadCount: Long?
)