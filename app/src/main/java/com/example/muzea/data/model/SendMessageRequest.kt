package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class SendMessageRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("messageType")
    val messageType: String = "TEXT"
)