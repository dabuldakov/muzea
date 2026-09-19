package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatRepository(
    private val apiService: ChatApiService,
    private val tokenManager: TokenManager
) {

    suspend fun loadChats(): Flow<NetworkResult<List<ChatResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
                return@flow
            }

            var response = apiService.getChats()
            if (response.code() == 401) {
                tokenManager.clearChatToken()
                if (ensureChatAuth()) {
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
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
                return@flow
            }

            var response = apiService.getContacts()
            if (response.code() == 401) {
                tokenManager.clearChatToken()
                if (ensureChatAuth()) {
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
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
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
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
                return@flow
            }

            var response = apiService.createPrivateChat(CreatePrivateChatRequest(userUuid))
            if (response.code() == 401) {
                tokenManager.clearChatToken()
                if (ensureChatAuth()) {
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
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
                return@flow
            }

            var response = apiService.getMessages(chatUuid)
            if (response.code() == 401) {
                tokenManager.clearChatToken()
                if (ensureChatAuth()) {
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
            if (!ensureChatAuth()) {
                emit(NetworkResult.Error("Chat auth failed. Please log in again."))
                return@flow
            }

            var response = apiService.sendMessage(chatUuid, SendMessageRequest(text))
            if (response.code() == 401) {
                tokenManager.clearChatToken()
                if (ensureChatAuth()) {
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

    private suspend fun ensureChatAuth(): Boolean {
        val currentUsername = tokenManager.getUsername()

        if (!tokenManager.getChatToken().isNullOrEmpty()) {
            if (currentUsername != null && tokenManager.getChatTokenUser() != currentUsername) {
                tokenManager.clearChatToken()
            } else {
                return true
            }
        }

        val username = currentUsername
        val password = tokenManager.getPassword()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return false

        return tryLogin(username, password) || tryRegister(username, password)
    }

    private suspend fun tryLogin(username: String, password: String): Boolean = try {
        val response = apiService.login(ChatLoginRequest(username, password))
        if (response.isSuccessful && response.body() != null) {
            tokenManager.saveChatToken(response.body()!!.token)
            tokenManager.saveChatTokenUser(username)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    private suspend fun tryRegister(username: String, password: String): Boolean = try {
        val email = tokenManager.getEmail() ?: "$username@example.com"
        val response = apiService.register(ChatRegisterRequest(username, email, password))
        if (response.isSuccessful && response.body() != null) {
            tokenManager.saveChatToken(response.body()!!.token)
            tokenManager.saveChatTokenUser(username)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}