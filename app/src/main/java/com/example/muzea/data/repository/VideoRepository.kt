package com.example.muzea.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.VideoResponse
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class VideoRepository (
    private val apiService: ApiService
) {

    suspend fun getVideos(): Flow<NetworkResult<List<VideoResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getVideos()
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load videos: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun getVideoById(id: Long): Flow<NetworkResult<VideoResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getVideoById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load video: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun uploadVideo(
        title: String,
        description: String?,
        filePart: MultipartBody.Part
    ): Flow<NetworkResult<VideoResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.uploadVideo(
                title.toRequestBody(),
                description?.toRequestBody(),
                filePart
            )
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Upload failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun streamVideo(fileName: String): Flow<NetworkResult<ResponseBody>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.streamVideo(fileName)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to stream video: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun downloadThumbnail(thumbnailUrl: String): Flow<NetworkResult<Bitmap>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.downloadFile(thumbnailUrl)
            if (response.isSuccessful && response.body() != null) {
                val bytes = response.body()!!.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                emit(NetworkResult.Success(bitmap))
            } else {
                emit(NetworkResult.Error("Failed to load thumbnail"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}