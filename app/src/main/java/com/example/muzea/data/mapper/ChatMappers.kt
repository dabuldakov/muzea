package com.example.muzea.data.mapper

import com.example.muzea.data.model.MessagePreview as MessagePreviewDto
import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.model.ChatReceipt
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
    unreadCount = unreadCount,
    peerDeleted = peerDeleted
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
    messageId = messageId,
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
    updatedAt = updatedAt,
    attachments = attachments.orEmpty().mapNotNull { it.toDomainAttachment() },
    power = power,
    senderDeleted = senderDeleted,
    reactions = reactions.orEmpty().map {
        com.example.muzea.domain.model.MessageReaction(
            emoji = it.emoji,
            count = it.count,
            mine = it.mine,
            users = it.users.orEmpty().map { user ->
                com.example.muzea.domain.model.ReactionUser(
                    userUuid = user.userUuid,
                    at = user.at
                )
            }
        )
    }
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

fun com.example.muzea.data.model.ChatReceiptResponse.toDomain(): ChatReceipt = ChatReceipt(
    userUuid = userUuid,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl,
    joinedAt = joinedAt,
    lastDeliveredMessageId = lastDeliveredMessageId,
    lastDeliveredAt = lastDeliveredAt,
    lastReadMessageId = lastReadMessageId,
    lastReadAt = lastReadAt
)

/** Вложение сообщения; null, если сервер не отдал URL файла. */
fun com.example.muzea.data.model.AttachmentResponse.toDomainAttachment(): com.example.muzea.domain.model.MessageAttachment? {
    val url = fileUrl ?: return null
    return com.example.muzea.domain.model.MessageAttachment(
        fileUrl = url,
        thumbnailUrl = thumbnailUrl,
        type = type,
        width = width,
        height = height,
        mp4Url = mp4Url,
        gifKey = gifKey,
        attachmentUuid = attachmentUuid,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        duration = duration
    )
}
