package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class MessageResponse(
    @SerializedName("messageUuid")
    val messageUuid: String,
    /** Монотонный id в БД: по нему сравниваются watermark-и статусов. */
    @SerializedName("messageId")
    val messageId: Long? = null,
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
    val updatedAt: String?,
    @SerializedName("attachments")
    val attachments: List<AttachmentResponse>? = null,
    @SerializedName("reactions")
    val reactions: List<ReactionResponse>? = null,
    @SerializedName("power")
    val power: Int = 0,
    /** Автор удалён: показываем «Удалённый пользователь». */
    @SerializedName("senderDeleted")
    val senderDeleted: Boolean = false
)

/** Реакция на сообщение: эмодзи произвольный (в пределах лимита сервера). */
data class ReactionResponse(
    @SerializedName("emoji") val emoji: String,
    @SerializedName("count") val count: Int,
    @SerializedName("mine") val mine: Boolean,
    @SerializedName("users") val users: List<ReactionUserResponse>? = null
)

/** Кто поставил реакцию и когда. */
data class ReactionUserResponse(
    @SerializedName("userUuid") val userUuid: String?,
    @SerializedName("at") val at: String?
)

/** Вложение сообщения (в т.ч. GIF). */
data class AttachmentResponse(
    @SerializedName("attachmentUuid") val attachmentUuid: String?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String?,
    @SerializedName("mp4Url") val mp4Url: String? = null,
    @SerializedName("type") val type: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("gifKey") val gifKey: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("duration") val duration: Int? = null
)
/** Запрос реакции: эмодзи произвольный. */
data class ReactionRequest(
    @SerializedName("emoji") val emoji: String
)
