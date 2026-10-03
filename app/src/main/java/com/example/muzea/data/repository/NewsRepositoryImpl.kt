package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.domain.model.News
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.NewsRepository]. */
@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : com.example.muzea.domain.repository.NewsRepository {

    override suspend fun getNews(page: Int, size: Int): Flow<NetworkResult<List<News>>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getNews(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.content.map { it.toDomain() }))
            } else {
                emit(NetworkResult.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun getNewsById(id: Long): Flow<NetworkResult<News>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getNewsById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.toDomain()))
            } else {
                emit(NetworkResult.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<NetworkResult<Long>> = flow {
        emit(NetworkResult.Loading())
        try {
            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.createNews(title.toRequestBody(), content.toRequestBody(), videoId, imagePart)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.id))
            } else {
                emit(NetworkResult.Error("Failed to create news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteNews(id: Long): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.deleteNews(id)
            if (response.isSuccessful) {
                emit(NetworkResult.Success(Unit))
            } else if (response.code() == 403) {
                emit(NetworkResult.Error("Only the author can delete this news"))
            } else {
                emit(NetworkResult.Error("Failed to delete news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}
