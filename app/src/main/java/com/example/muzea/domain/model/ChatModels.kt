package com.example.muzea.domain.model

/**
 * Доменные модели чата — чистые Kotlin-типы без Retrofit/Gson-аннотаций.
 *
 * Имена полей совпадают с полями сетевых DTO, чтобы переход с DTO на домен
 * не разъезжался по всему UI. DTO теперь живут только в data-слое и
 * преобразуются мапперами.
 */

data class Chat(
    val chatUuid: String,
    val chatType: String?,
    val title: String?,
    val avatarUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val participantCount: Long?,
    val lastMessage: MessagePreview?,
    val unreadCount: Long?
)

data class MessagePreview(
    val messageUuid: String?,
    val text: String?,
    val senderId: Long?,
    val senderName: String?,
    val createdAt: String?
)

data class Message(
    val messageUuid: String,
    val chatUuid: String?,
    val senderId: Long?,
    val senderUuid: String?,
    val senderName: String?,
    val senderAvatar: String?,
    val text: String?,
    val messageType: String?,
    val replyToMessageUuid: String?,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val isPinned: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)

data class ChatParticipant(
    val userUuid: String?,
    val userId: Long?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val fullName: String?,
    val nickname: String?,
    val avatarUrl: String?,
    val role: String?,
    val online: Boolean,
    val lastSeenAt: String?,
    val joinedAt: String?
) {
    fun displayName(): String = fullName ?: username ?: "Participant"
}

data class Contact(
    val contactUuid: String,
    val contactUserId: Long?,
    val contactUserUuid: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val contactName: String?,
    val isOnline: Boolean,
    val lastSeenAt: String?,
    val addedAt: String?
) {
    fun displayName(): String = contactName ?: fullName ?: username ?: "Contact"
}

data class Presence(
    val online: Boolean,
    val lastSeenAt: String?
)

data class Avatar(
    val avatarUrl: String?
)
