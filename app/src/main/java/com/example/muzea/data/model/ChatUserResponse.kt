package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatUserResponse(
    @SerializedName("userUuid")
    val userUuid: String,
    @SerializedName("username")
    val username: String?,
    @SerializedName("email")
    val email: String?,
    @SerializedName("firstName")
    val firstName: String?,
    @SerializedName("lastName")
    val lastName: String?,
    @SerializedName("fullName")
    val fullName: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    // Бэкенд отдаёт "online" (@JsonProperty на boolean-поле isOnline),
    // поэтому "isOnline" здесь всегда давало бы false.
    @SerializedName("online")
    val isOnline: Boolean
)