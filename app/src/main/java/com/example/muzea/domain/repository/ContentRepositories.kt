package com.example.muzea.domain.repository

import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.Video
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import java.io.File

/** Доменные контракты новостей и видео. */

interface NewsRepository {
    suspend fun getNews(page: Int = 0, size: Int = 20): Flow<NetworkResult<List<News>>>
    suspend fun getNewsById(id: Long): Flow<NetworkResult<News>>
    suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<NetworkResult<Long>>
    suspend fun deleteNews(id: Long): Flow<NetworkResult<Unit>>
}

interface VideoRepository {
    suspend fun getVideos(): Flow<NetworkResult<List<Video>>>
    fun cachedVideos(): List<Video>
    suspend fun getVideoById(id: Long): Flow<NetworkResult<Video>>
    suspend fun uploadVideo(
        title: String,
        description: String?,
        filePart: MultipartBody.Part,
        thumbnailPart: MultipartBody.Part?
    ): Flow<NetworkResult<Video>>
    suspend fun deleteVideo(id: Long): Flow<NetworkResult<Unit>>
}
