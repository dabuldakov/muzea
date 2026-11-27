package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Комментарий к новости. */
data class NewsCommentResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("newsId")
    val newsId: Long,
    @SerializedName("content")
    val content: String,
    @SerializedName("author")
    val author: String,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("authorAvatarUrl")
    val authorAvatarUrl: String? = null
)

/** Страница комментариев: по возрастанию id, более ранние — по before. */
data class NewsCommentsResponse(
    @SerializedName("comments")
    val comments: List<NewsCommentResponse>,
    @SerializedName("total")
    val total: Long,
    @SerializedName("hasMore")
    val hasMore: Boolean,
    @SerializedName("nextBefore")
    val nextBefore: Long?
)

/** Ответ на идемпотентный POST/DELETE лайка. */
data class NewsLikeResponse(
    @SerializedName("liked")
    val liked: Boolean,
    @SerializedName("likes")
    val likes: Long
)

/** Новости, которые лайкнул текущий пользователь. */
data class LikedNewsResponse(
    @SerializedName("likedIds")
    val likedIds: List<Long>
)
