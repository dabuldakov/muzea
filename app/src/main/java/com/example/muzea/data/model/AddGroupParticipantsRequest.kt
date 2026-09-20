package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class AddGroupParticipantsRequest(
    @SerializedName("memberUuids")
    val memberUuids: List<String>
)