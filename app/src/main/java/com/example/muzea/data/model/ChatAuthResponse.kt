package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatAuthResponse(
    @SerializedName("token")
    val token: String,
    @SerializedName("refreshToken")
    val refreshToken: String?,
    @SerializedName("userUuid")
    val userUuid: String?,
    @SerializedName("username")
    val username: String?,
    @SerializedName("email")
    val email: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?
)