package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Батч ошибок, который клиент отправляет при старте. */
data class ErrorReportRequest(
    @SerializedName("events") val events: List<ErrorEventRequest>,
    @SerializedName("overflowed") val overflowed: Boolean = false
)

data class ErrorEventRequest(
    @SerializedName("eventId") val eventId: String,
    @SerializedName("time") val time: String,
    @SerializedName("level") val level: String,
    @SerializedName("tag") val tag: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("stack") val stack: String?,
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("buildType") val buildType: String,
    @SerializedName("device") val device: String,
    @SerializedName("os") val os: String,
    @SerializedName("userId") val userId: String?
)

data class ErrorReportResponse(
    @SerializedName("accepted") val accepted: Int
)
