package com.example.muzea.domain.repository

import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.Presence
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Контракты чат-фичи на доменных моделях. Реализации живут в data-слое,
 * привязка — через Hilt. UI и ViewModel зависят только от этих интерфейсов.
 */

interface ChatRepository {
    suspend fun loadChats(): Flow<NetworkResult<List<Chat>>>
    fun cachedChats(): List<Chat>
    suspend fun loadChatParticipants(chatUuid: String): Flow<NetworkResult<List<ChatParticipant>>>
    suspend fun createPrivateChat(userUuid: String): Flow<NetworkResult<Chat>>
    suspend fun findPrivateChatWith(userUuid: String): Chat?
    suspend fun createGroupChat(title: String, memberUuids: List<String>): Flow<NetworkResult<Chat>>
    suspend fun addGroupParticipants(chatUuid: String, memberUuids: List<String>): Flow<NetworkResult<Unit>>
}

interface MessageRepository {
    suspend fun loadMessages(chatUuid: String): Flow<NetworkResult<List<Message>>>
    fun cachedMessages(chatUuid: String): List<Message>
    suspend fun sendMessage(chatUuid: String, text: String): Flow<NetworkResult<Message>>
    suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean
}

interface ContactRepository {
    suspend fun loadContacts(): Flow<NetworkResult<List<Contact>>>
    suspend fun addContactByUsername(username: String): Flow<NetworkResult<Contact>>
    suspend fun loadPresence(userUuids: List<String>): Map<String, Presence>
}

interface AvatarRepository {
    suspend fun loadAvatar(): Flow<NetworkResult<Avatar>>
    suspend fun uploadAvatar(file: File, mimeType: String): Flow<NetworkResult<Avatar>>
    suspend fun deleteAvatar(): Flow<NetworkResult<Avatar>>
    suspend fun uploadChatAvatar(
        chatUuid: String,
        file: File,
        mimeType: String
    ): Flow<NetworkResult<String>>
}

interface ChatSessionRepository {
    suspend fun deleteAccount(): Boolean
    suspend fun sendHeartbeat(): Boolean
    suspend fun logout(): Boolean
    suspend fun getTotalUnreadCount(): Flow<NetworkResult<Long>>
}
