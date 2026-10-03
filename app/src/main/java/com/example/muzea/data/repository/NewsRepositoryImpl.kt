package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.domain.model.News
import com.example.muzea.core.Resource
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

    override suspend fun getNews(page: Int, size: Int): Flow<Resource<List<News>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getNews(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.content.map { it.toDomain() }))
            } else {
                emit(Resource.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun getNewsById(id: Long): Flow<Resource<News>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getNewsById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<Resource<Long>> = flow {
        emit(Resource.Loading())
        try {
            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.createNews(title.toRequestBody(), content.toRequestBody(), videoId, imagePart)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.id))
            } else {
                emit(Resource.Error("Failed to create news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteNews(id: Long): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.deleteNews(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else if (response.code() == 403) {
                emit(Resource.Error("Only the author can delete this news"))
            } else {
                emit(Resource.Error("Failed to delete news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }
}
