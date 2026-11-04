package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class UpdateUserRequest(
    @SerializedName("fullName")
    val fullName: String?,
    @SerializedName("enabled")
    val enabled: Boolean = true
)