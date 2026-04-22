package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.UpdateUserRequest
import com.example.muzea.data.model.UserResponse
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UserRepository(
    private val apiService: ApiService
) {

    suspend fun getCurrentUser(): Flow<NetworkResult<UserResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                android.util.Log.d("UserRepository", "User: $user")
                emit(NetworkResult.Success(user))
            } else {
                emit(NetworkResult.Error("Failed to load user: ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error: ${e.message}", e)
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun updateUser(
        userId: Long,
        fullName: String?,
        email: String?,
        enabled: Boolean = true
    ): Flow<NetworkResult<UserResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            // Передаем все поля
            val request = UpdateUserRequest(
                fullName = fullName,
                email = email,
                enabled = enabled
            )
            val response = apiService.updateUser(userId, request)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("UserRepository", "Error response: $errorBody")
                emit(NetworkResult.Error("Update failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Update error: ${e.message}", e)
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}