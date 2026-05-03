package com.example.muzea.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.NewsCreateResponse
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class NewsRepository constructor(
    private val apiService: ApiService
) {

    suspend fun getNews(page: Int = 0, size: Int = 20): Flow<NetworkResult<List<NewsResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getNews(page, size)
            if (response.isSuccessful && response.body() != null) {
                val pageResponse = response.body()!!
                emit(NetworkResult.Success(pageResponse.content))
            } else {
                emit(NetworkResult.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun getNewsById(id: Long): Flow<NetworkResult<NewsResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getNewsById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<NetworkResult<NewsCreateResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.createNews(title, content, videoId, imagePart)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to create news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun downloadImage(imageUrl: String): Flow<NetworkResult<Bitmap>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.downloadFile(imageUrl)
            if (response.isSuccessful && response.body() != null) {
                val bytes = response.body()!!.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                emit(NetworkResult.Success(bitmap))
            } else {
                emit(NetworkResult.Error("Failed to load news image"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}