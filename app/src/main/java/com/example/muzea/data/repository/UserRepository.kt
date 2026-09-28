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

    /**
     * Удаление новостей, видео и профиля на основном бэкенде.
     *
     * 404 считается успехом: аккаунт мог быть удалён при предыдущей попытке,
     * где чат-сервер отвечает раньше основного. Повторять удаление нужно
     * иначе — данные на одном из серверов остались бы навсегда.
     */
    suspend fun deleteAccount(
        userId: Long
    ): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.deleteUser(userId)
            if (response.isSuccessful || response.code() == 404) {
                emit(NetworkResult.Success(Unit))
            } else {
                emit(NetworkResult.Error("Delete failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Delete error: ${e.message}", e)
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}