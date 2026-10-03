package com.example.muzea.data.mapper

import com.example.muzea.data.model.MessagePreview as MessagePreviewDto
import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessagePreview
import com.example.muzea.domain.model.Presence

/**
 * Преобразование сетевых DTO чата в доменные модели.
 *
 * DTO не покидают data-слой: наружу (в domain/repository и UI) уходят только
 * чистые доменные типы.
 */

fun com.example.muzea.data.model.ChatResponse.toDomain(): Chat = Chat(
    chatUuid = chatUuid,
    chatType = chatType,
    title = title,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    participantCount = participantCount,
    lastMessage = lastMessage?.toDomain(),
    unreadCount = unreadCount
)

fun MessagePreviewDto.toDomain(): MessagePreview = MessagePreview(
    messageUuid = messageUuid,
    text = text,
    senderId = senderId,
    senderName = senderName,
    createdAt = createdAt
)

fun com.example.muzea.data.model.MessageResponse.toDomain(): Message = Message(
    messageUuid = messageUuid,
    chatUuid = chatUuid,
    senderId = senderId,
    senderUuid = senderUuid,
    senderName = senderName,
    senderAvatar = senderAvatar,
    text = text,
    messageType = messageType,
    replyToMessageUuid = replyToMessageUuid,
    isEdited = isEdited,
    isDeleted = isDeleted,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun com.example.muzea.data.model.ContactResponse.toDomain(): Contact = Contact(
    contactUuid = contactUuid,
    contactUserId = contactUserId,
    contactUserUuid = contactUserUuid,
    username = username,
    firstName = firstName,
    lastName = lastName,
    fullName = fullName,
    avatarUrl = avatarUrl,
    contactName = contactName,
    isOnline = isOnline,
    lastSeenAt = lastSeenAt,
    addedAt = addedAt
)

fun com.example.muzea.data.model.ChatParticipantResponse.toDomain(): ChatParticipant = ChatParticipant(
    userUuid = userUuid,
    userId = userId,
    username = username,
    firstName = firstName,
    lastName = lastName,
    fullName = fullName,
    nickname = nickname,
    avatarUrl = avatarUrl,
    role = role,
    online = online,
    lastSeenAt = lastSeenAt,
    joinedAt = joinedAt
)

fun com.example.muzea.data.model.PresenceResponse.toDomain(): Presence = Presence(
    online = online,
    lastSeenAt = lastSeenAt
)

fun com.example.muzea.data.model.AvatarResponse.toDomain(): Avatar = Avatar(
    avatarUrl = avatarUrl
)
