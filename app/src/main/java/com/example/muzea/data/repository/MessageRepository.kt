package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Сообщения в переписке: загрузка, кэш, отправка и отметка прочитанным. */
@Singleton
class MessageRepository @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) {

    suspend fun loadMessages(chatUuid: String): Flow<NetworkResult<List<MessageResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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
                val content = response.body()!!.content
                ChatMessagesCache.put(chatUuid, content)
                emit(NetworkResult.Success(content))
            } else {
                emit(NetworkResult.Error("Failed to load messages: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Мгновенный доступ к уже загруженной переписке без обращения к сети.
     * Используется, чтобы чат открывался сразу, а обновление шло фоном.
     */
    fun cachedMessages(chatUuid: String): List<MessageResponse> = ChatMessagesCache.get(chatUuid)

    suspend fun sendMessage(chatUuid: String, text: String): Flow<NetworkResult<MessageResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to send message: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean {
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
