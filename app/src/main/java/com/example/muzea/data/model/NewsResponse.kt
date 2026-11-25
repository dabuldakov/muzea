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
    val publishedAt: String,
    @SerializedName("views")
    val views: Long? = null,
    @SerializedName("likes")
    val likes: Long? = null,
    @SerializedName("commentsCount")
    val commentsCount: Long? = null,
    @SerializedName("likedByMe")
    val likedByMe: Boolean? = null
)
