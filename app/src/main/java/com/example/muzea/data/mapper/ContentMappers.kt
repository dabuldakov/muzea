package com.example.muzea.data.mapper

import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.NewsCommentsPage
import com.example.muzea.domain.model.Video

/** Преобразование сетевых DTO новостей/видео в доменные модели. */

fun com.example.muzea.data.model.VideoResponse.toDomain(): Video = Video(
    id = id,
    title = title,
    description = description,
    url = url,
    thumbnailUrl = thumbnailUrl,
    fileSize = fileSize,
    duration = duration,
    durationSeconds = durationSeconds,
    views = views,
    likes = likes,
    uploadedBy = uploadedBy,
    uploadedAt = uploadedAt
)

fun com.example.muzea.data.model.NewsResponse.toDomain(): News = News(
    id = id,
    title = title,
    content = content,
    imageUrl = imageUrl,
    relatedVideo = relatedVideo?.toDomain(),
    author = author,
    publishedAt = publishedAt,
    views = views ?: 0,
    likes = likes ?: 0,
    commentsCount = commentsCount ?: 0,
    likedByMe = likedByMe ?: false
)

fun com.example.muzea.data.model.NewsCommentResponse.toDomain(): NewsComment = NewsComment(
    id = id,
    newsId = newsId,
    content = content,
    author = author,
    createdAt = createdAt
)

fun com.example.muzea.data.model.NewsCommentsResponse.toDomain(): NewsCommentsPage = NewsCommentsPage(
    comments = comments.map { it.toDomain() },
    total = total,
    hasMore = hasMore,
    nextBefore = nextBefore
)
