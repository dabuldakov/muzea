package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatRegisterRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)