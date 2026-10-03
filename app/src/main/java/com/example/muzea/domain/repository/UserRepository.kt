package com.example.muzea.domain.repository

import com.example.muzea.domain.model.User
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

/** Доменный контракт профиля пользователя основного бэкенда. */
interface UserRepository {
    suspend fun getCurrentUser(): Flow<NetworkResult<User>>
    suspend fun updateUser(
        userId: Long,
        fullName: String?,
        email: String?,
        enabled: Boolean = true
    ): Flow<NetworkResult<User>>
    suspend fun deleteAccount(userId: Long): Flow<NetworkResult<Unit>>
}
