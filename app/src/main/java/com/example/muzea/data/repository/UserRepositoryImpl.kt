package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.UpdateUserRequest
import com.example.muzea.domain.model.User
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.UserRepository]. */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : com.example.muzea.domain.repository.UserRepository {

    override suspend fun getCurrentUser(): Flow<NetworkResult<User>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.toDomain()))
            } else {
                emit(NetworkResult.Error("Failed to load user: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun updateUser(
        userId: Long,
        fullName: String?,
        email: String?,
        enabled: Boolean
    ): Flow<NetworkResult<User>> = flow {
        emit(NetworkResult.Loading())
        try {
            val request = UpdateUserRequest(
                fullName = fullName,
                email = email,
                enabled = enabled
            )
            val response = apiService.updateUser(userId, request)
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.toDomain()))
            } else {
                emit(NetworkResult.Error("Update failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Удаление новостей, видео и профиля на основном бэкенде.
     *
     * 404 считается успехом: аккаунт мог быть удалён при предыдущей попытке,
     * где чат-сервер отвечает раньше основного.
     */
    override suspend fun deleteAccount(userId: Long): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.deleteUser(userId)
            if (response.isSuccessful || response.code() == 404) {
                emit(NetworkResult.Success(Unit))
            } else {
                emit(NetworkResult.Error("Delete failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}
