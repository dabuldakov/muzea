package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class VideoResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("url")
    val url: String,
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String?,
    @SerializedName("fileSize")
    val fileSize: Long?,
    @SerializedName("duration")
    val duration: String?,
    @SerializedName("views")
    val views: Int,
    @SerializedName("likes")
    val likes: Int?,
    @SerializedName("uploadedBy")
    val uploadedBy: String,
    @SerializedName("uploadedAt")
    val uploadedAt: String
)