package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Квитанция участника чата: watermark-и доставки и прочтения. */
data class ChatReceiptResponse(
    @SerializedName("userUuid") val userUuid: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("joinedAt") val joinedAt: String?,
    @SerializedName("lastDeliveredMessageId") val lastDeliveredMessageId: Long?,
    @SerializedName("lastDeliveredAt") val lastDeliveredAt: String?,
    @SerializedName("lastReadMessageId") val lastReadMessageId: Long?,
    @SerializedName("lastReadAt") val lastReadAt: String?
)
