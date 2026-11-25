package com.example.muzea.domain.model

import com.example.muzea.utils.MediaUrl

/** Доменные модели новостей и видео (имена полей совпадают с сетевыми DTO). */

data class News(
    val id: Long,
    val title: String,
    val content: String,
    val imageUrl: String?,
    val relatedVideo: Video?,
    val author: String,
    val publishedAt: String,
    val views: Long = 0,
    val likes: Long = 0,
    val commentsCount: Long = 0,
    val likedByMe: Boolean = false
)

/** Комментарий к новости. */
data class NewsComment(
    val id: Long,
    val newsId: Long,
    val content: String,
    val author: String,
    val createdAt: String
)

/** Страница комментариев в хронологическом порядке. */
data class NewsCommentsPage(
    val comments: List<NewsComment>,
    val total: Long,
    val hasMore: Boolean,
    val nextBefore: Long?
)

data class Video(
    val id: Long,
    val title: String,
    val description: String?,
    val url: String,
    val thumbnailUrl: String?,
    val fileSize: Long?,
    val duration: String?,
    val durationSeconds: Double? = null,
    val views: Int,
    val likes: Int?,
    val uploadedBy: String,
    val uploadedAt: String
) {
    fun getFullThumbnailUrl(baseUrl: String): String? =
        MediaUrl.absolute(baseUrl, thumbnailUrl)

    fun getFullVideoUrl(baseUrl: String): String =
        MediaUrl.absolute(baseUrl, url) ?: url
}
