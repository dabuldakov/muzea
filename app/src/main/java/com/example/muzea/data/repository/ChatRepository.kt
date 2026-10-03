package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.AddGroupParticipantsRequest
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.CreateGroupChatRequest
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Диалоги: список чатов, создание приватных и групповых, участники.
 *
 * Сообщения, контакты, присутствие, аватары и сессия живут в отдельных
 * репозиториях — раньше всё это было в одном классе на 555 строк.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) {

    private companion object {
        const val PRIVATE_CHAT_TYPE = "PRIVATE"
    }

    suspend fun loadChats(): Flow<NetworkResult<List<ChatResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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

    suspend fun loadChatParticipants(
        chatUuid: String
    ): Flow<NetworkResult<List<ChatParticipantResponse>>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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

    suspend fun createPrivateChat(userUuid: String): Flow<NetworkResult<ChatResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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
                val chat = response.body()!!
                PrivateChatCache.put(userUuid, chat)
                emit(NetworkResult.Success(chat))
            } else {
                emit(NetworkResult.Error("Failed to create chat: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Ищет уже существующий приватный чат с пользователем.
     *
     * У приватных чатов нет названия (title == null), поэтому сопоставляем по
     * участникам: перебираем приватные чаты и смотрим, есть ли среди участников
     * нужный userUuid. Нужен, чтобы повторное нажатие на контакт открывало
     * существующую переписку, а не создавало дубликат.
     */
    suspend fun findPrivateChatWith(userUuid: String): ChatResponse? {
        PrivateChatCache.get(userUuid)?.let { return it }

        // Свежий список: только что созданный чат ещё может не успеть попасть
        // в кэш, и мы бы создали дубликат.
        var chats: List<ChatResponse> = emptyList()
        loadChats().collect { result ->
            if (result is NetworkResult.Success) chats = result.data ?: emptyList()
        }

        var match: ChatResponse? = null
        for (chat in chats) {
            if (!chat.chatType.equals(PRIVATE_CHAT_TYPE, ignoreCase = true)) continue

            var participants: List<ChatParticipantResponse> = emptyList()
            loadChatParticipants(chat.chatUuid).collect { result ->
                if (result is NetworkResult.Success) participants = result.data ?: emptyList()
            }

            // Заполняем кэш по всем найденным приватным чатам: тогда следующие
            // тапы по любым контактам будут мгновенными.
            for (participant in participants) {
                val uuid = participant.userUuid ?: continue
                PrivateChatCache.put(uuid, chat)
            }

            if (match == null && participants.any { it.userUuid == userUuid }) {
                match = chat
            }
        }
        return match
    }

    suspend fun createGroupChat(
        title: String,
        memberUuids: List<String>
    ): Flow<NetworkResult<ChatResponse>> = flow {
        emit(NetworkResult.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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
                emit(NetworkResult.Error(chatAuthManager.authFailureMessage()))
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
}
