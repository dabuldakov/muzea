package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("userName")
    val userName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("fullName")
    val fullName: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    @SerializedName("role")
    val role: String,
    @SerializedName("createdAt")
    val createdAt: String?,
    @SerializedName("enabled")
    val enabled: Boolean = true
)

