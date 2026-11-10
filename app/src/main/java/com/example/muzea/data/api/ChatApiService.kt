package com.example.muzea.data.api

import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.AttachmentResponse
import com.example.muzea.data.model.AddGroupParticipantsRequest
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.data.model.BlockUserRequest
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatReportRequest
import com.example.muzea.data.model.ChatReceiptResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreateGroupChatRequest
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.GifResponse
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
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.POST
import retrofit2.http.Streaming
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

    /** Сообщения старше указанного — для догрузки истории вверх. */
    /** Новые сообщения после указанного времени (ISO-8601): дешёвый поллинг. */
    @GET("/api/messages/{chatUuid}/after/{timestamp}")
    suspend fun getMessagesAfter(
        @Path("chatUuid") chatUuid: String,
        @Path("timestamp") timestamp: String
    ): Response<List<MessageResponse>>

    @GET("/api/messages/{chatUuid}/before/{messageUuid}")
    suspend fun getMessagesBefore(
        @Path("chatUuid") chatUuid: String,
        @Path("messageUuid") messageUuid: String,
        @Query("limit") limit: Int = 50
    ): Response<List<MessageResponse>>

    /** Поставить или снять реакцию; сервер возвращает сводку по сообщению. */
    /** Диагностика: батч новых ошибок клиента. */
    @POST("/api/telemetry/errors")
    suspend fun uploadErrorEvents(
        @Body request: com.example.muzea.data.model.ErrorReportRequest
    ): Response<com.example.muzea.data.model.ErrorReportResponse>

    @POST("/api/messages/{messageUuid}/reactions")
    suspend fun toggleReaction(
        @Path("messageUuid") messageUuid: String,
        @Body request: com.example.muzea.data.model.ReactionRequest
    ): Response<List<com.example.muzea.data.model.ReactionResponse>>

    @POST("/api/messages/{chatUuid}")
    suspend fun sendMessage(
        @Path("chatUuid") chatUuid: String,
        @Body request: SendMessageRequest
    ): Response<MessageResponse>

    /** Редактирование своего текстового сообщения. */
    @retrofit2.http.PUT("/api/messages/{messageUuid}")
    suspend fun editMessage(
        @Path("messageUuid") messageUuid: String,
        @Body request: com.example.muzea.data.model.EditMessageRequest
    ): Response<MessageResponse>

    /** Удаление своего сообщения (soft delete у всех). */
    @DELETE("/api/messages/{messageUuid}")
    suspend fun deleteMessage(
        @Path("messageUuid") messageUuid: String
    ): Response<Unit>

    /** Пересылка сообщения в другой чат. */
    @POST("/api/messages/forward")
    suspend fun forwardMessage(
        @Body request: com.example.muzea.data.model.ForwardMessageRequest
    ): Response<MessageResponse>

    /** Закрепление сообщения в чате. */
    @GET("/api/messages/{chatUuid}/pin/{messageUuid}")
    suspend fun pinMessage(
        @Path("chatUuid") chatUuid: String,
        @Path("messageUuid") messageUuid: String
    ): Response<Unit>

    /** Открепление сообщения. */
    @DELETE("/api/messages/{chatUuid}/pin/{messageUuid}")
    suspend fun unpinMessage(
        @Path("chatUuid") chatUuid: String,
        @Path("messageUuid") messageUuid: String
    ): Response<Unit>

    /** Закреплённые сообщения чата. */
    @GET("/api/messages/{chatUuid}/pinned")
    suspend fun getPinnedMessages(
        @Path("chatUuid") chatUuid: String
    ): Response<List<MessageResponse>>

    @POST("/api/messages/{chatUuid}/read")
    suspend fun markMessagesAsRead(
        @Path("chatUuid") chatUuid: String,
        @Query("upToMessageUuid") upToMessageUuid: String
    ): Response<Unit>

    /** Подтверждение доставки всей ленты до указанного сообщения. */
    @POST("/api/messages/{chatUuid}/delivered")
    suspend fun markMessagesAsDelivered(
        @Path("chatUuid") chatUuid: String,
        @Query("upToMessageUuid") upToMessageUuid: String
    ): Response<Unit>

    /** Квитанции участников: докуда доставлено и докуда прочитано. */
    @GET("/api/chats/{chatUuid}/receipts")
    suspend fun getChatReceipts(
        @Path("chatUuid") chatUuid: String
    ): Response<List<ChatReceiptResponse>>

    @Multipart
    @POST("/api/attachments/upload")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
        @Part("chatUuid") chatUuid: RequestBody,
        @Part thumbnail: MultipartBody.Part?,
        @Part("width") width: RequestBody?,
        @Part("height") height: RequestBody?,
        @Part("duration") duration: RequestBody?
    ): Response<AttachmentResponse>

    @Streaming
    @GET("/api/attachments/{attachmentUuid}")
    suspend fun downloadAttachment(
        @Path("attachmentUuid") attachmentUuid: String
    ): Response<ResponseBody>

    @POST("/api/reports")
    suspend fun reportContent(
        @Body request: ChatReportRequest
    ): Response<Unit>

    @POST("/api/blocked")
    suspend fun blockUser(
        @Body request: BlockUserRequest
    ): Response<Unit>

    /** «Мои» недавние гифки (с нашего API). */
    @GET("/api/gifs/recent")
    suspend fun getRecentGifs(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30
    ): Response<List<GifResponse>>

    /** Гифки конкретного чата (с нашего API). */
    @GET("/api/gifs/chat/{chatUuid}")
    suspend fun getChatGifs(
        @Path("chatUuid") chatUuid: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30
    ): Response<List<GifResponse>>

}
