package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

data class NewsResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    @SerializedName("relatedVideo")
    val relatedVideo: VideoResponse?,
    @SerializedName("author")
    val author: String,
    @SerializedName("publishedAt")
    val publishedAt: String
)