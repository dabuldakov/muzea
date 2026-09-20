package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatParticipantResponse(
    @SerializedName("userUuid")
    val userUuid: String?,
    @SerializedName("userId")
    val userId: Long?,
    @SerializedName("username")
    val username: String?,
    @SerializedName("firstName")
    val firstName: String?,
    @SerializedName("lastName")
    val lastName: String?,
    @SerializedName("fullName")
    val fullName: String?,
    @SerializedName("nickname")
    val nickname: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    @SerializedName("role")
    val role: String?,
    @SerializedName("online")
    val online: Boolean,
    @SerializedName("lastSeenAt")
    val lastSeenAt: String?,
    @SerializedName("joinedAt")
    val joinedAt: String?
) {
    fun displayName(): String = fullName ?: username ?: "Participant"
}