package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.domain.model.Video
import com.example.muzea.core.Resource
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

    override suspend fun getVideos(): Flow<Resource<List<Video>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getVideos()
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.map { it.toDomain() }
                VideoListCache.put(content)
                emit(Resource.Success(content))
            } else {
                emit(Resource.Error("Failed to load videos: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedVideos(): List<Video> = VideoListCache.get()

    override suspend fun getVideoById(id: Long): Flow<Resource<Video>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getVideoById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to load video: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun uploadVideo(
        title: String,
        description: String?,
        filePart: MultipartBody.Part,
        thumbnailPart: MultipartBody.Part?
    ): Flow<Resource<Video>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.uploadVideo(
                title.toRequestBody(),
                description?.toRequestBody(),
                filePart,
                thumbnailPart
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Upload failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteVideo(id: Long): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.deleteVideo(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Delete failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }
}
