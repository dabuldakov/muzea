package com.example.muzea.data.api

import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.ChatAuthResponse
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.PageResponse
import com.example.muzea.data.model.SendMessageRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("/api/contacts")
    suspend fun getContacts(): Response<List<ContactResponse>>

    @POST("/api/contacts")
    suspend fun addContact(
        @Body request: AddContactRequest
    ): Response<ContactResponse>

    @GET("/api/users/by-username/{username}")
    suspend fun getUserByUsername(
        @Path("username") username: String
    ): Response<ChatUserResponse>

    @POST("/api/chats/private")
    suspend fun createPrivateChat(
        @Body request: CreatePrivateChatRequest
    ): Response<ChatResponse>

    @GET("/api/messages/{chatUuid}")
    suspend fun getMessages(
        @Path("chatUuid") chatUuid: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<PageResponse<MessageResponse>>

    @POST("/api/messages/{chatUuid}")
    suspend fun sendMessage(
        @Path("chatUuid") chatUuid: String,
        @Body request: SendMessageRequest
    ): Response<MessageResponse>
}