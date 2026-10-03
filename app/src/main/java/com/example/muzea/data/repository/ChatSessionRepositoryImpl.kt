package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.ChatSessionRepository]. */
@Singleton
class ChatSessionRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) : com.example.muzea.domain.repository.ChatSessionRepository {

    override suspend fun deleteAccount(): Boolean {
        return try {
            check(chatAuthManager.isAuthenticated()) { chatAuthManager.authFailureMessage() }
            val response = apiService.deleteAccount()
            response.isSuccessful || response.code() == 401 || response.code() == 404
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun sendHeartbeat(): Boolean = try {
        if (!chatAuthManager.isAuthenticated()) {
            false
        } else {
            apiService.sendHeartbeat().isSuccessful
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun logout(): Boolean = try {
        if (chatAuthManager.isAuthenticated()) {
            apiService.logout().isSuccessful
        } else {
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    override suspend fun getTotalUnreadCount(): Flow<NetworkResult<Long>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = chatAuthManager.authenticatedRequest { apiService.getTotalUnreadCount() }
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.count))
            } else {
                emit(NetworkResult.Error("Failed to load unread count: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}
