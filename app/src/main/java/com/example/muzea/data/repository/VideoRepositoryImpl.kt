package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.domain.model.Video
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.VideoRepository]. */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : com.example.muzea.domain.repository.VideoRepository {

    override suspend fun getVideos(): Flow<NetworkResult<List<Video>>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getVideos()
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.map { it.toDomain() }
                VideoListCache.put(content)
                emit(NetworkResult.Success(content))
            } else {
                emit(NetworkResult.Error("Failed to load videos: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedVideos(): List<Video> = VideoListCache.get()

    override suspend fun getVideoById(id: Long): Flow<NetworkResult<Video>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getVideoById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.toDomain()))
            } else {
                emit(NetworkResult.Error("Failed to load video: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun uploadVideo(
        title: String,
        description: String?,
        filePart: MultipartBody.Part,
        thumbnailPart: MultipartBody.Part?
    ): Flow<NetworkResult<Video>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.uploadVideo(
                title.toRequestBody(),
                description?.toRequestBody(),
                filePart,
                thumbnailPart
            )
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.toDomain()))
            } else {
                emit(NetworkResult.Error("Upload failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteVideo(id: Long): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.deleteVideo(id)
            if (response.isSuccessful) {
                emit(NetworkResult.Success(Unit))
            } else {
                emit(NetworkResult.Error("Delete failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}
