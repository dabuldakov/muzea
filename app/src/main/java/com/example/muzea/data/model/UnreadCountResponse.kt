package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class UnreadCountResponse(
    @SerializedName("count")
    val count: Long
)
