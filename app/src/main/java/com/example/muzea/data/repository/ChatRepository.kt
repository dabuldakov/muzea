package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.example.muzea.data.model.AvatarResponse

class ChatRepository(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) {

    suspend fun loadAvatar(): Flow<NetworkResult<AvatarResponse>> = avatarRequest {
        val response = authenticatedRequest { apiService.getMyProfile() }
        check(response.isSuccessful && response.body() != null) { "Could not load avatar (${response.code()})" }
        AvatarResponse(response.body()!!.avatarUrl)
    }

    suspend fun uploadAvatar(file: File, mimeType: String): Flow<NetworkResult<AvatarResponse>> = avatarRequest {
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mimeType.toMediaType()))
        val response = authenticatedRequest { apiService.uploadAvatar(part) }
        check(response.isSuccessful && response.body()?.avatarUrl != null) {
            "Could not upload avatar (${response.code()}). Use a JPEG or PNG image up to 5 MB."
        }
        response.body()!!
    }

    suspend fun deleteAvatar(): Flow<NetworkResult<AvatarResponse>> = avatarRequest {
        val response = authenticatedRequest { apiService.deleteAvatar() }
        check(response.isSuccessful) { "Could not delete avatar (${response.code()})" }
        AvatarResponse(null)
    }

    private fun avatarRequest(action: suspend () -> AvatarResponse): Flow<NetworkResult<AvatarResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            emit(NetworkResult.Success(action()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Avatar request failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun authFailureMessage(): String =
        "Chat auth failed. " + (chatAuthManager.lastFailureMessage ?: "Please log in again.")

    private suspend fun <T> authenticatedRequest(action: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        check(chatAuthManager.isAuthenticated()) { authFailureMessage() }
        var response = action()
        if (response.code() == 401) {
            chatAuthManager.invalidate()
            check(chatAuthManager.isAuthenticated()) { authFailureMessage() }
            response = action()
        }
        return response
    }

    suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean {
        return try {
            authenticatedRequest { apiService.markMessagesAsRead(chatUuid, upToMessageUuid) }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getTotalUnreadCount(): Flow<NetworkResult<Long>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = authenticatedRequest { apiService.getTotalUnreadCount() }
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

    suspend fun loadChats(): Flow<NetworkResult<List<ChatResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.getChats()
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.getChats()
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load chats: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun loadContacts(): Flow<NetworkResult<List<ContactResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.getContacts()
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.getContacts()
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load contacts: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun addContactByUsername(username: String): Flow<NetworkResult<ContactResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            val userResponse = apiService.getUserByUsername(username)
            if (!userResponse.isSuccessful || userResponse.body() == null) {
                emit(NetworkResult.Error("User not found: $username"))
                return@flow
            }

            val userUuid = userResponse.body()!!.userUuid
            val response = apiService.addContact(AddContactRequest(userUuid, null))

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to add contact: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun createPrivateChat(userUuid: String): Flow<NetworkResult<ChatResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.createPrivateChat(CreatePrivateChatRequest(userUuid))
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.createPrivateChat(CreatePrivateChatRequest(userUuid))
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to create chat: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun loadMessages(chatUuid: String): Flow<NetworkResult<List<MessageResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
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
                emit(NetworkResult.Success(content))
            } else {
                emit(NetworkResult.Error("Failed to load messages: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun sendMessage(chatUuid: String, text: String): Flow<NetworkResult<MessageResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
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
}
