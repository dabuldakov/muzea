package com.example.muzea.data.api

import com.example.muzea.data.model.ChatAuthResponse
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ChatApiService {

    @POST("/api/auth/login")
    suspend fun login(
        @Body request: ChatLoginRequest
    ): Response<ChatAuthResponse>

    @POST("/api/auth/register")
    suspend fun register(
        @Body request: ChatRegisterRequest
    ): Response<ChatAuthResponse>

    @GET("/api/chats")
    suspend fun getChats(): Response<List<ChatResponse>>
}