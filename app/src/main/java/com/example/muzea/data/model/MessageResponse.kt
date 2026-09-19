package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class MessageResponse(
    @SerializedName("messageUuid")
    val messageUuid: String,
    @SerializedName("chatUuid")
    val chatUuid: String?,
    @SerializedName("senderId")
    val senderId: Long?,
    @SerializedName("senderUuid")
    val senderUuid: String?,
    @SerializedName("senderName")
    val senderName: String?,
    @SerializedName("senderAvatar")
    val senderAvatar: String?,
    @SerializedName("text")
    val text: String?,
    @SerializedName("messageType")
    val messageType: String?,
    @SerializedName("replyToMessageUuid")
    val replyToMessageUuid: String?,
    @SerializedName("isEdited")
    val isEdited: Boolean,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("isPinned")
    val isPinned: Boolean,
    @SerializedName("createdAt")
    val createdAt: String?,
    @SerializedName("updatedAt")
    val updatedAt: String?
)