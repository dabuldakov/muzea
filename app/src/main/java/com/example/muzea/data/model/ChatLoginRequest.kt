package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatLoginRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String
)