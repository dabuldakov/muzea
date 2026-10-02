package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.AddGroupParticipantsRequest
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.CreateGroupChatRequest
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.PresenceResponse
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

    private companion object {
        /**
         * Размер чанка batch-запроса статусов. Держим заметно ниже серверного
         * лимита (200), чтобы URL со списком UUID не разрастался до проблем
         * у прокси и серверов.
         */
        const val PRESENCE_BATCH_SIZE = 100
    }

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

    /**
     * Полное удаление аккаунта на чат-сервере.
     *
     * Намеренно без [authenticatedRequest]: после удаления сервер отвечает 401,
     * а повторная аутентификация в [ChatAuthManager] зарегистрировала бы заново
     * аккаунт с тем же именем. Поэтому 401 и 404 здесь считаются успехом —
     * аккаунта уже нет. true означает, что данных на сервере не осталось.
     */
    suspend fun deleteAccount(): Boolean {
        return try {
            check(chatAuthManager.isAuthenticated()) { authFailureMessage() }
            val response = apiService.deleteAccount()
            response.isSuccessful || response.code() == 401 || response.code() == 404
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadChatAvatar(
        chatUuid: String,
        file: File,
        mimeType: String
    ): Flow<NetworkResult<String>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            val part = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody(mimeType.toMediaType())
            )
            var response = apiService.uploadChatAvatar(chatUuid, part)
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.uploadChatAvatar(chatUuid, part)
                }
            }

            if (response.isSuccessful && response.body() != null) {
                val path = response.body()!!.string().trim()
                emit(NetworkResult.Success(path))
            } else {
                emit(
                    NetworkResult.Error(
                        "Failed to upload avatar (${response.code()}). Use a JPEG or PNG image up to 5 MB."
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

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
                val content = response.body()!!
                ChatListCache.put(content)
                emit(NetworkResult.Success(content))
            } else {
                emit(NetworkResult.Error("Failed to load chats: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Мгновенный доступ к последнему известному списку чатов без обращения к сети.
     * Используется, чтобы список открывался сразу, а обновление шло фоном.
     */
    fun cachedChats(): List<ChatResponse> = ChatListCache.get()

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

    /**
     * Heartbeat «я на переднем плане».
     *
     * Тихий метод без Flow: ошибки здесь нечего показывать пользователю —
     * упавший heartbeat означает лишь «статус обновится чуть позже», а окно
     * TTL специально шире интервала. Возвращаем признак успеха, чтобы
     * вызывающий мог отличить сетевую проблему от штатной отправки.
     */
    suspend fun sendHeartbeat(): Boolean = try {
        if (!chatAuthManager.isAuthenticated()) {
            false
        } else {
            apiService.sendHeartbeat().isSuccessful
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /**
     * Пакетный статус присутствия по UUID.
     *
     * Бэкенд ограничивает размер пачки, поэтому список режется на чанки и
     * ответы склеиваются: иначе запрос на 300 контактов упал бы с 400.
     * Пустой вход даёт пустой результат без обращения к сети.
     */
    suspend fun loadPresence(userUuids: List<String>): Map<String, PresenceResponse> {
        val wanted = userUuids.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, PresenceResponse>()
        for (chunk in wanted.chunked(PRESENCE_BATCH_SIZE)) {
            try {
                var response = apiService.getPresence(chunk)
                if (response.code() == 401) {
                    chatAuthManager.invalidate()
                    if (!chatAuthManager.isAuthenticated()) return result
                    response = apiService.getPresence(chunk)
                }
                if (response.isSuccessful) {
                    response.body()?.forEach { result[it.userUuid] = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Частичный результат лучше пустого: контакты, чей статус
                // пришёл, покажут реальное состояние, остальные — прошлый.
                Unit
            }
        }
        return result
    }

    /**
     * Серверный разлогин.
     *
     * Вызывается перед очисткой локального токена: пока сессия жива, сервер
     * считает пользователя онлайн, и его статус «залипнет» в чужих контактах.
     */
    suspend fun logout(): Boolean = try {
        if (chatAuthManager.isAuthenticated()) {
            apiService.logout().isSuccessful
        } else {
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Разлогин нельзя отменять из-за сети: локальные токены всё равно
        // чистим вызывающий код, сервер же догасит сессию по TTL.
        true
    }

    suspend fun loadChatParticipants(
        chatUuid: String
    ): Flow<NetworkResult<List<ChatParticipantResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.getChatParticipants(chatUuid)
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.getChatParticipants(chatUuid)
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to load participants: ${response.message()}"))
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

    suspend fun createGroupChat(
        title: String,
        memberUuids: List<String>
    ): Flow<NetworkResult<ChatResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.createGroupChat(CreateGroupChatRequest(title, memberUuids))
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.createGroupChat(CreateGroupChatRequest(title, memberUuids))
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!))
            } else {
                emit(NetworkResult.Error("Failed to create group chat: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    suspend fun addGroupParticipants(
        chatUuid: String,
        memberUuids: List<String>
    ): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(authFailureMessage()))
                return@flow
            }

            var response = apiService.addGroupParticipants(
                chatUuid,
                AddGroupParticipantsRequest(memberUuids)
            )
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.addGroupParticipants(
                        chatUuid,
                        AddGroupParticipantsRequest(memberUuids)
                    )
                }
            }

            if (response.isSuccessful) {
                emit(NetworkResult.Success(Unit))
            } else {
                emit(NetworkResult.Error("Failed to add members: ${response.message()}"))
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
                ChatMessagesCache.put(chatUuid, content)
                emit(NetworkResult.Success(content))
            } else {
                emit(NetworkResult.Error("Failed to load messages: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Мгновенный доступ к уже загруженной переписке без обращения к сети.
     * Используется, чтобы чат открывался сразу, а обновление шло фоном.
     */
    fun cachedMessages(chatUuid: String): List<MessageResponse> = ChatMessagesCache.get(chatUuid)

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
