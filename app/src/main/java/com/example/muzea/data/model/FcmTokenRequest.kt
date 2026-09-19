package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class FcmTokenRequest(
    @SerializedName("token")
    val token: String,
    @SerializedName("deviceId")
    val deviceId: String?
)
