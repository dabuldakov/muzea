package com.example.muzea.data.repository

import javax.inject.Inject
import javax.inject.Singleton

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.model.LoginRequest
import com.example.muzea.data.model.RegisterRequest
import com.example.muzea.core.Resource
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {

    suspend fun login(username: String, password: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                tokenManager.saveUsername(authResponse.username)
                tokenManager.savePassword(password)
                emit(Resource.Success(authResponse.token))
            } else {
                emit(Resource.Error("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        fullName: String
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.register(RegisterRequest(username, email, password, fullName))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                tokenManager.saveUsername(authResponse.username)
                tokenManager.saveEmail(email)
                tokenManager.savePassword(password)
                emit(Resource.Success(authResponse.token))
            } else {
                emit(Resource.Error("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }

    fun isLoggedIn(): Boolean {
        return !tokenManager.getToken().isNullOrEmpty()
    }
}