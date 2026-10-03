package com.example.muzea.domain.repository

import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.Presence
import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Контракты чат-фичи на доменных моделях. Реализации живут в data-слое,
 * привязка — через Hilt. UI и ViewModel зависят только от этих интерфейсов.
 */

interface ChatRepository {
    suspend fun loadChats(): Flow<Resource<List<Chat>>>
    fun cachedChats(): List<Chat>
    suspend fun loadChatParticipants(chatUuid: String): Flow<Resource<List<ChatParticipant>>>
    suspend fun createPrivateChat(userUuid: String): Flow<Resource<Chat>>
    suspend fun findPrivateChatWith(userUuid: String): Chat?
    suspend fun createGroupChat(title: String, memberUuids: List<String>): Flow<Resource<Chat>>
    suspend fun addGroupParticipants(chatUuid: String, memberUuids: List<String>): Flow<Resource<Unit>>
}

interface MessageRepository {
    suspend fun loadMessages(chatUuid: String): Flow<Resource<List<Message>>>
    fun cachedMessages(chatUuid: String): List<Message>
    suspend fun sendMessage(chatUuid: String, text: String): Flow<Resource<Message>>
    suspend fun markMessagesAsRead(chatUuid: String, upToMessageUuid: String): Boolean
}

interface ContactRepository {
    suspend fun loadContacts(): Flow<Resource<List<Contact>>>
    suspend fun addContactByUsername(username: String): Flow<Resource<Contact>>
    suspend fun loadPresence(userUuids: List<String>): Map<String, Presence>
}

interface AvatarRepository {
    suspend fun loadAvatar(): Flow<Resource<Avatar>>
    suspend fun uploadAvatar(file: File, mimeType: String): Flow<Resource<Avatar>>
    suspend fun deleteAvatar(): Flow<Resource<Avatar>>
    suspend fun uploadChatAvatar(
        chatUuid: String,
        file: File,
        mimeType: String
    ): Flow<Resource<String>>
}

interface ChatSessionRepository {
    suspend fun deleteAccount(): Boolean
    suspend fun sendHeartbeat(): Boolean
    suspend fun logout(): Boolean
    suspend fun getTotalUnreadCount(): Flow<Resource<Long>>
}
