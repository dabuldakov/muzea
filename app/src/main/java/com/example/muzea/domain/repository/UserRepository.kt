package com.example.muzea.domain.repository

import com.example.muzea.domain.model.User
import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.Flow

/** Доменный контракт профиля пользователя основного бэкенда. */
interface UserRepository {
    suspend fun getCurrentUser(): Flow<Resource<User>>
    suspend fun updateUser(
        userId: Long,
        fullName: String?,
        email: String?,
        enabled: Boolean = true
    ): Flow<Resource<User>>
    suspend fun deleteAccount(userId: Long): Flow<Resource<Unit>>
}
