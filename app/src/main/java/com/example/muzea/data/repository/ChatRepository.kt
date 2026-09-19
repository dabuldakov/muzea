package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatResponse
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

    private suspend fun ensureChatAuth(): Boolean {
        if (!tokenManager.getChatToken().isNullOrEmpty()) return true

        val username = tokenManager.getUsername()
        val password = tokenManager.getPassword()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return false

        return tryLogin(username, password) || tryRegister(username, password)
    }

    private suspend fun tryLogin(username: String, password: String): Boolean = try {
        val response = apiService.login(ChatLoginRequest(username, password))
        if (response.isSuccessful && response.body() != null) {
            tokenManager.saveChatToken(response.body()!!.token)
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
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}