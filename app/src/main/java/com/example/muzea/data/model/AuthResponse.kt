package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("token")
    val token: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("role")
    val role: String
)