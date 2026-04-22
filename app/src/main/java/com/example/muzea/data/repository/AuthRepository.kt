package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.LoginRequest
import com.example.muzea.data.model.RegisterRequest
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {

    suspend fun login(username: String, password: String): Flow<NetworkResult<String>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                tokenManager.saveUsername(authResponse.username)
                emit(NetworkResult.Success(authResponse.token))
            } else {
                emit(NetworkResult.Error("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        fullName: String
    ): Flow<NetworkResult<String>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = apiService.register(RegisterRequest(username, email, password, fullName))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                tokenManager.saveUsername(authResponse.username)
                emit(NetworkResult.Success(authResponse.token))
            } else {
                emit(NetworkResult.Error("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }

    fun isLoggedIn(): Boolean {
        return !tokenManager.getToken().isNullOrEmpty()
    }
}