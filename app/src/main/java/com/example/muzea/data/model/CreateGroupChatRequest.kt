package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class CreateGroupChatRequest(
    @SerializedName("title")
    val title: String,
    @SerializedName("memberUuids")
    val memberUuids: List<String>
)