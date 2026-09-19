package com.example.muzea.ui.video

import com.example.muzea.data.model.VideoResponse

/**
 * Чистая функция фильтрации ленты видео: оставляет только видео самого пользователя.
 *
 * Вынесена из VideoViewModel, чтобы поведение было тривиально тестируемым и
 * не зависело от Android-окружения.
 */
object VideoFeedFilter {

    fun filterOwn(
        videos: List<VideoResponse>,
        ownUsername: String?
    ): List<VideoResponse> {
        val me = ownUsername?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()

        return videos.filter { item ->
            item.uploadedBy.trim() == me
        }
    }
}
