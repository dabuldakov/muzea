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
    // Имя ключа обязано совпадать с бэкендом: ContactDto помечает поле
    // @JsonProperty("online"), поэтому в JSON приходит "online", а не "isOnline".
    // Со старым "isOnline" Gson молча подставлял false — «в сети» не горел ни
    // у кого. Держим @SerializedName("online") синхронно с ContactDto.
    @SerializedName("online")
    val isOnline: Boolean,
    @SerializedName("lastSeenAt")
    val lastSeenAt: String?,
    @SerializedName("addedAt")
    val addedAt: String?
) {
    fun displayName(): String = contactName ?: fullName ?: username ?: "Contact"
}