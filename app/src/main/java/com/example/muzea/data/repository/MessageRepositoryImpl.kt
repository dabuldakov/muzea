package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.mapper.toDomainAttachment
import com.example.muzea.data.model.GifRequest
import com.example.muzea.data.model.EditMessageRequest
import com.example.muzea.data.model.ForwardMessageRequest
import com.example.muzea.data.model.SendMessageRequest
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.MessageRepository]. */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager,
    private val gifCache: GifCache,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : com.example.muzea.domain.repository.MessageRepository {

    override suspend fun loadMessages(chatUuid: String): Flow<Resource<List<Message>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.getMessages(chatUuid)
            }

            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.content.map { it.toDomain() }
                ChatMessagesCache.put(chatUuid, content)
                emit(Resource.Success(content))
            } else {
                emit(Resource.Error("Failed to load messages: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun loadMessagesBefore(
        chatUuid: String,
        beforeMessageUuid: String,
        limit: Int
    ): Flow<Resource<List<Message>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.getMessagesBefore(chatUuid, beforeMessageUuid, limit)
            }

            if (response.isSuccessful && response.body() != null) {
                val older = response.body()!!.map { it.toDomain() }
                ChatMessagesCache.prependOlder(chatUuid, older)
                emit(Resource.Success(older))
            } else {
                emit(Resource.Error("Failed to load older messages: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedMessages(chatUuid: String): List<Message> = ChatMessagesCache.get(chatUuid)

    override suspend fun sendMessage(
        chatUuid: String,
        text: String,
        attachments: List<String>,
        messageType: String,
        replyToMessageUuid: String?
    ): Flow<Resource<Message>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.sendMessage(
                    chatUuid,
                    SendMessageRequest(
                        text = text,
                        // TEXT — сервер выведет тип по вложениям; VOICE/VIDEO_NOTE
                        // передаём явно, чтобы отрисовать голосовое/кружок.
                        messageType = messageType,
                        replyToMessageUuid = replyToMessageUuid,
                        attachments = attachments.takeIf { it.isNotEmpty() }
                    )
                )
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to send message: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun sendGif(
        chatUuid: String,
        gif: Gif,
        replyToMessageUuid: String?
    ): Flow<Resource<Message>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val request = SendMessageRequest(
                text = "",
                messageType = "GIF",
                replyToMessageUuid = replyToMessageUuid,
                gif = GifRequest(
                    provider = gif.provider,
                    providerGifId = gif.providerGifId,
                    title = gif.title,
                    url = gif.url,
                    previewUrl = gif.previewUrl,
                    mp4Url = gif.mp4Url,
                    width = gif.width,
                    height = gif.height
                )
            )

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.sendMessage(chatUuid, request)
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to send GIF: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun editMessage(
        messageUuid: String,
        text: String
    ): Flow<Resource<Message>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.editMessage(messageUuid, EditMessageRequest(text))
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to edit message: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteMessage(messageUuid: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.deleteMessage(messageUuid)
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun forwardMessage(
        messageUuid: String,
        targetChatUuid: String
    ): Flow<Resource<Message>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val response = chatAuthManager.retryOnceAfterUnauthorized {
                apiService.forwardMessage(ForwardMessageRequest(messageUuid, targetChatUuid))
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to forward message: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun pinMessage(chatUuid: String, messageUuid: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.pinMessage(chatUuid, messageUuid)
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun unpinMessage(chatUuid: String, messageUuid: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.unpinMessage(chatUuid, messageUuid)
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadPinned(chatUuid: String): List<Message> {
        return try {
            val response = chatAuthManager.authenticatedRequest {
                apiService.getPinnedMessages(chatUuid)
            }
            if (response.isSuccessful) {
                response.body()?.map { it.toDomain() }.orEmpty()
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun cacheSentGif(chatUuid: String, gif: Gif, mine: Boolean) {
        gifCache.prepend(GifCache.chatKey(chatUuid), gif)
        if (mine) {
            gifCache.prepend(GifCache.recentKey(), gif)
        }
    }

    override suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean {
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

    override suspend fun markMessagesAsDelivered(
        chatUuid: String,
        upToMessageUuid: String
    ): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.markMessagesAsDelivered(chatUuid, upToMessageUuid)
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadChatReceipts(
        chatUuid: String
    ): List<com.example.muzea.domain.model.ChatReceipt>? {
        return try {
            val response = chatAuthManager.authenticatedRequest {
                apiService.getChatReceipts(chatUuid)
            }
            if (response.isSuccessful) {
                response.body()?.map { it.toDomain() }.orEmpty()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun reportMessage(messageUuid: String, reason: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.reportContent(
                    com.example.muzea.data.model.ChatReportRequest(
                        contentType = "MESSAGE",
                        contentUuid = messageUuid,
                        reason = reason
                    )
                )
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun blockUser(userUuid: String): Boolean {
        return try {
            chatAuthManager.authenticatedRequest {
                apiService.blockUser(
                    com.example.muzea.data.model.BlockUserRequest(blockUserUuid = userUuid)
                )
            }.isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun uploadAttachment(
        chatUuid: String,
        prepared: PreparedAttachment,
        onProgress: (Int) -> Unit
    ): Resource<MessageAttachment> {
        return try {
            if (!chatAuthManager.isAuthenticated()) {
                return Resource.Error(chatAuthManager.authFailureMessage())
            }

            val filePart = MultipartBody.Part.createFormData(
                "file",
                prepared.name,
                ProgressRequestBody(
                    prepared.file,
                    prepared.mimeType.toMediaTypeOrNull(),
                    onProgress
                )
            )
            val thumbPart = prepared.thumbnail?.let { thumb ->
                MultipartBody.Part.createFormData(
                    "thumbnail",
                    thumb.name,
                    thumb.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
            }

            val response = chatAuthManager.authenticatedRequest {
                apiService.uploadAttachment(
                    file = filePart,
                    chatUuid = chatUuid.toRequestBody("text/plain".toMediaTypeOrNull()),
                    thumbnail = thumbPart,
                    width = prepared.width?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    height = prepared.height?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    duration = prepared.duration?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                )
            }

            if (response.isSuccessful && response.body() != null) {
                val attachment = response.body()!!.toDomainAttachment()
                if (attachment != null) {
                    Resource.Success(attachment)
                } else {
                    Resource.Error("Server returned an empty attachment")
                }
            } else if (response.code() == 400) {
                Resource.Error("File was rejected by the server")
            } else {
                Resource.Error("Upload failed: ${response.message()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.message}")
        }
    }

    override suspend fun downloadAttachment(
        attachmentUuid: String,
        fileName: String?
    ): File? {
        return try {
            val response = chatAuthManager.authenticatedRequest {
                apiService.downloadAttachment(attachmentUuid)
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                null
            } else {
                val targetDir = File(
                    context.filesDir,
                    com.example.muzea.utils.Constants.CHAT_DOWNLOADS_DIR
                ).apply { mkdirs() }
                val safeName = (fileName ?: "file").replace(Regex("""[\\/:*?"<>|]"""), "_")
                val target = File(targetDir, safeName)
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
