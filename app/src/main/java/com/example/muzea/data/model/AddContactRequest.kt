package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class AddContactRequest(
    @SerializedName("contactUserUuid")
    val contactUserUuid: String,
    @SerializedName("contactName")
    val contactName: String?
)