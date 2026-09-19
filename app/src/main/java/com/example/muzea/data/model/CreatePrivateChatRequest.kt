package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class CreatePrivateChatRequest(
    @SerializedName("otherUserUuid")
    val otherUserUuid: String
)