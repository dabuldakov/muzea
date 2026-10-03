package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.domain.model.Message
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.MessageRepository]. */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) : com.example.muzea.domain.repository.MessageRepository {

    override suspend fun loadMessages(chatUuid: String): Flow<Resource<List<Message>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            var response = apiService.getMessages(chatUuid)
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.getMessages(chatUuid)
                }
            }

            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.content.map { it.toDomain() }
                ChatMessagesCache.put(chatUuid, content)
                emit(Resource.Success(content))
            } else {
                emit(Resource.Error("Failed to load messages: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedMessages(chatUuid: String): List<Message> = ChatMessagesCache.get(chatUuid)

    override suspend fun sendMessage(chatUuid: String, text: String): Flow<Resource<Message>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            var response = apiService.sendMessage(chatUuid, SendMessageRequest(text))
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.sendMessage(chatUuid, SendMessageRequest(text))
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to send message: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.markMessagesAsRead(chatUuid, upToMessageUuid)
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
