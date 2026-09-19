package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class ContactResponse(
    @SerializedName("contactUuid")
    val contactUuid: String,
    @SerializedName("contactUserId")
    val contactUserId: Long?,
    @SerializedName("contactUserUuid")
    val contactUserUuid: String?,
    @SerializedName("username")
    val username: String?,
    @SerializedName("firstName")
    val firstName: String?,
    @SerializedName("lastName")
    val lastName: String?,
    @SerializedName("fullName")
    val fullName: String?,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    @SerializedName("contactName")
    val contactName: String?,
    @SerializedName("isOnline")
    val isOnline: Boolean,
    @SerializedName("lastSeenAt")
    val lastSeenAt: String?,
    @SerializedName("addedAt")
    val addedAt: String?
) {
    fun displayName(): String = contactName ?: fullName ?: username ?: "Contact"
}