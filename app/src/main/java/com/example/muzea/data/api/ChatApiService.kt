package com.example.muzea.data.api

import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.AddGroupParticipantsRequest
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.data.model.ChatAuthResponse
import com.example.muzea.data.model.ChatLoginRequest
import com.example.muzea.data.model.ChatRegisterRequest
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreateGroupChatRequest
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.PageResponse
import com.example.muzea.data.model.PresenceResponse
import com.example.muzea.data.model.SendMessageRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApiService {

    @GET("/api/users/me")
    suspend fun getMyProfile(): Response<ChatUserResponse>

    @retrofit2.http.PUT("/api/users/me/fcm-token")
    suspend fun registerFcmToken(
        @Body request: com.example.muzea.data.model.FcmTokenRequest
    ): Response<Unit>

    @Multipart
    @POST("/api/users/me/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part
    ): Response<AvatarResponse>

    @DELETE("/api/users/me/avatar")
    suspend fun deleteAvatar(): Response<Unit>

    /**
     * Полное удаление аккаунта на чат-сервере вместе с сообщениями, контактами,
     * вложениями и FCM-токенами.
     */
    @DELETE("/api/users/me")
    suspend fun deleteAccount(): Response<Unit>

    @Multipart
    @POST("/api/chats/{chatUuid}/avatar")
    suspend fun uploadChatAvatar(
        @Path("chatUuid") chatUuid: String,
        @Part file: MultipartBody.Part
    ): Response<okhttp3.ResponseBody>

    @POST("/api/auth/login")
    suspend fun login(
        @Body request: ChatLoginRequest
    ): Response<ChatAuthResponse>

    @POST("/api/auth/register")
    suspend fun register(
        @Body request: ChatRegisterRequest
    ): Response<ChatAuthResponse>

    /**
     * Серверный разлогин. Обязателен, а не «просто почистить токен локально»:
     * без него сессия остаётся живой и сервер считает пользователя онлайн.
     */
    @POST("/api/auth/logout")
    suspend fun logout(): Response<Unit>

    /**
     * Heartbeat «я на переднем плане». Продлевает окно онлайна на сервере.
     *
     * Отправляется, пока приложение видно, с интервалом больше серверного TTL
     * с запасом: несколько пропущенных запросов не должны гасить статус.
     */
    @POST("/api/presence/heartbeat")
    suspend fun sendHeartbeat(): Response<Unit>

    /**
     * Пакетный статус присутствия для списка контактов.
     *
     * Отдельный лёгкий запрос вместо перезагрузки /api/contacts: список контактов
     * нужен редко, статус нужен постоянно. Бэкенд ограничивает размер пачки,
     * поэтому клиент режет список на чанки.
     */
    @GET("/api/presence")
    suspend fun getPresence(
        @Query("userUuids") userUuids: List<String>
    ): Response<List<PresenceResponse>>

    @GET("/api/chats")
    suspend fun getChats(): Response<List<ChatResponse>>

    @GET("/api/chats/unread-count/all")
    suspend fun getTotalUnreadCount(): Response<com.example.muzea.data.model.UnreadCountResponse>

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

    @POST("/api/chats/group")
    suspend fun createGroupChat(
        @Body request: CreateGroupChatRequest
    ): Response<ChatResponse>

    @POST("/api/chats/{chatUuid}/participants")
    suspend fun addGroupParticipants(
        @Path("chatUuid") chatUuid: String,
        @Body request: AddGroupParticipantsRequest
    ): Response<Unit>

    @GET("/api/chats/{chatUuid}/participants")
    suspend fun getChatParticipants(
        @Path("chatUuid") chatUuid: String
    ): Response<List<ChatParticipantResponse>>

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

    @POST("/api/messages/{chatUuid}/read")
    suspend fun markMessagesAsRead(
        @Path("chatUuid") chatUuid: String,
        @Query("upToMessageUuid") upToMessageUuid: String
    ): Response<Unit>
}
