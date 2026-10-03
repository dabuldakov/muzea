package com.example.muzea.data.mapper

import com.example.muzea.domain.model.News
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
    publishedAt = publishedAt
)
