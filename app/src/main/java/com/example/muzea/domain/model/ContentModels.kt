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
    val publishedAt: String
)

data class Video(
    val id: Long,
    val title: String,
    val description: String?,
    val url: String,
    val thumbnailUrl: String?,
    val fileSize: Long?,
    val duration: String?,
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
