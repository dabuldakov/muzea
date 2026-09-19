package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ChatLoginRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("deviceId")
    val deviceId: String? = null,
    @SerializedName("deviceName")
    val deviceName: String? = null,
    @SerializedName("deviceType")
    val deviceType: String? = null
)
