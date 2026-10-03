package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.AddGroupParticipantsRequest
import com.example.muzea.data.model.CreateGroupChatRequest
import com.example.muzea.data.model.CreatePrivateChatRequest
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [com.example.muzea.domain.repository.ChatRepository]:
 * диалоги, приватные/групповые чаты и участники.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) : com.example.muzea.domain.repository.ChatRepository {

    private companion object {
        const val PRIVATE_CHAT_TYPE = "PRIVATE"
    }

    override suspend fun loadChats(): Flow<Resource<List<Chat>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
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
                val content = response.body()!!.map { it.toDomain() }
                ChatListCache.put(content)
                emit(Resource.Success(content))
            } else {
                emit(Resource.Error("Failed to load chats: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedChats(): List<Chat> = ChatListCache.get()

    override suspend fun loadChatParticipants(
        chatUuid: String
    ): Flow<Resource<List<ChatParticipant>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
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
                emit(Resource.Success(response.body()!!.map { it.toDomain() }))
            } else {
                emit(Resource.Error("Failed to load participants: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun createPrivateChat(userUuid: String): Flow<Resource<Chat>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
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
                val chat = response.body()!!.toDomain()
                PrivateChatCache.put(userUuid, chat)
                emit(Resource.Success(chat))
            } else {
                emit(Resource.Error("Failed to create chat: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
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
    override suspend fun findPrivateChatWith(userUuid: String): Chat? {
        PrivateChatCache.get(userUuid)?.let { return it }

        // Свежий список: только что созданный чат ещё может не успеть попасть
        // в кэш, и мы бы создали дубликат.
        var chats: List<Chat> = emptyList()
        loadChats().collect { result ->
            if (result is Resource.Success) chats = result.data ?: emptyList()
        }

        var match: Chat? = null
        for (chat in chats) {
            if (!chat.chatType.equals(PRIVATE_CHAT_TYPE, ignoreCase = true)) continue

            var participants: List<ChatParticipant> = emptyList()
            loadChatParticipants(chat.chatUuid).collect { result ->
                if (result is Resource.Success) participants = result.data ?: emptyList()
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

    override suspend fun createGroupChat(
        title: String,
        memberUuids: List<String>
    ): Flow<Resource<Chat>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
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
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to create group chat: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun addGroupParticipants(
        chatUuid: String,
        memberUuids: List<String>
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
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
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Failed to add members: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }
}
