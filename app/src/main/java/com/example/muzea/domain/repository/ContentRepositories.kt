package com.example.muzea.domain.repository

import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.NewsCommentsPage
import com.example.muzea.domain.model.Video
import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import java.io.File

/** Доменные контракты новостей и видео. */

interface NewsRepository {
    suspend fun getNews(page: Int = 0, size: Int = 20): Flow<Resource<List<News>>>
    suspend fun getNewsById(id: Long): Flow<Resource<News>>
    suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<Resource<Long>>

    /** Редактирование новости автором; фото можно заменить или удалить. */
    suspend fun updateNews(
        id: Long,
        title: String,
        content: String,
        videoId: Long?,
        removeVideo: Boolean,
        imageFile: File?,
        removeImage: Boolean
    ): Flow<Resource<News>>

    suspend fun deleteNews(id: Long): Flow<Resource<Unit>>

    /** Ставит лайк; возвращает актуальное число лайков. */
    suspend fun likeNews(id: Long): Flow<Resource<Long>>

    /** Снимает лайк; возвращает актуальное число лайков. */
    suspend fun unlikeNews(id: Long): Flow<Resource<Long>>

    /** Новости, лайкнутые текущим пользователем (для ленты). */
    suspend fun getLikedNewsIds(): Flow<Resource<Set<Long>>>

    /** Учитывает просмотр новости; ошибка не влияет на экран. */
    suspend fun registerView(id: Long)

    suspend fun getComments(
        newsId: Long,
        before: Long? = null,
        size: Int = 20
    ): Flow<Resource<NewsCommentsPage>>

    suspend fun addComment(newsId: Long, content: String): Flow<Resource<NewsComment>>

    suspend fun deleteComment(newsId: Long, commentId: Long): Flow<Resource<Unit>>

    /** Пожаловаться на новость (UGC). true — сервер принял жалобу. */
    suspend fun reportNews(id: Long, reason: String): Boolean

    /** Пожаловаться на комментарий (UGC). true — сервер принял жалобу. */
    suspend fun reportComment(commentId: Long, reason: String): Boolean
}

interface VideoRepository {
    suspend fun getVideos(): Flow<Resource<List<Video>>>
    fun cachedVideos(): List<Video>
    suspend fun getVideoById(id: Long): Flow<Resource<Video>>

    /**
     * Загружает видео напрямую в объектное хранилище по presigned URL.
     * [onProgress] вызывается с процентом 0..100 из фонового потока.
     */
    suspend fun uploadVideo(
        title: String,
        description: String?,
        file: File,
        contentType: String,
        thumbnail: ByteArray?,
        onProgress: (Int) -> Unit
    ): Flow<Resource<Video>>
    suspend fun deleteVideo(id: Long): Flow<Resource<Unit>>

    /** Пожаловаться на видео (UGC). true — сервер принял жалобу. */
    suspend fun reportVideo(id: Long, reason: String): Boolean
}
