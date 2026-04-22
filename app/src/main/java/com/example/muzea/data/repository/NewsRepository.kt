package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
        videoId: Long?
    ): Flow<NetworkResult<NewsResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.createNews(title, content, videoId)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to create news: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}