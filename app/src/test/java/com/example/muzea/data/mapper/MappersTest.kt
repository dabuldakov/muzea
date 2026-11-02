package com.example.muzea.data.mapper

import com.example.muzea.data.model.AttachmentResponse
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatReceiptResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.MessagePreview
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.model.PresenceResponse
import com.example.muzea.data.model.UserResponse
import com.example.muzea.data.model.VideoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun `chat response maps nested last message`() {
        val chat = ChatResponse(
            chatUuid = "c1",
            chatType = "PRIVATE",
            title = "Чат",
            avatarUrl = null,
            createdAt = "2026-01-01",
            updatedAt = "2026-01-02",
            participantCount = 2,
            lastMessage = MessagePreview("m1", "hi", 1, "Anna", "2026-01-02"),
            unreadCount = 3
        ).toDomain()

        assertEquals("c1", chat.chatUuid)
        assertEquals("hi", chat.lastMessage?.text)
        assertEquals(3L, chat.unreadCount)
    }

    @Test
    fun `message response keeps attachments with a url and drops empty ones`() {
        val message = message(
            attachments = listOf(
                attachment(fileUrl = "https://cdn/1.gif", gifKey = "g1", mp4Url = "https://cdn/1.mp4"),
                attachment(fileUrl = null)
            )
        ).toDomain()

        assertEquals(1, message.attachments.size)
        val attachment = message.attachments.single()
        assertEquals("https://cdn/1.gif", attachment.fileUrl)
        assertEquals("g1", attachment.gifKey)
        assertEquals("https://cdn/1.mp4", attachment.mp4Url)
    }

    @Test
    fun `message without attachments maps to an empty list`() {
        assertTrue(message(attachments = null).toDomain().attachments.isEmpty())
    }

    @Test
    fun `contact and participant responses map one-to-one`() {
        val contact = ContactResponse(
            contactUuid = "ct1",
            contactUserId = 1,
            contactUserUuid = "u1",
            username = "anna",
            firstName = "Anna",
            lastName = null,
            fullName = "Anna",
            avatarUrl = "https://a/1.jpg",
            contactName = "Аня",
            isOnline = true,
            lastSeenAt = "2026-01-01",
            addedAt = "2025-12-01"
        ).toDomain()

        assertEquals("Аня", contact.contactName)
        assertTrue(contact.isOnline)

        val participant = ChatParticipantResponse(
            userUuid = "u1",
            userId = 1,
            username = "anna",
            firstName = null,
            lastName = null,
            fullName = "Anna",
            nickname = "Аня",
            avatarUrl = null,
            role = "ADMIN",
            online = true,
            lastSeenAt = null,
            joinedAt = "2025-01-01"
        ).toDomain()

        assertEquals("ADMIN", participant.role)
        assertEquals("Аня", participant.nickname)
    }

    @Test
    fun `presence and avatar responses map their single field`() {
        assertEquals(true, PresenceResponse("u1", true, "now").toDomain().online)
        assertEquals("https://a/2.jpg", AvatarResponse("https://a/2.jpg").toDomain().avatarUrl)
        assertNull(AvatarResponse(null).toDomain().avatarUrl)
    }

    @Test
    fun `video and news responses map nested video`() {
        val video = VideoResponse(
            id = 5,
            title = "Клип",
            description = "desc",
            url = "/stream/5.mp4",
            thumbnailUrl = "/thumb/5.jpg",
            fileSize = 10,
            duration = "00:10",
            views = 7,
            likes = 2,
            uploadedBy = "vovan",
            uploadedAt = "2026-01-01"
        )
        val mapped = video.toDomain()

        assertEquals("Клип", mapped.title)
        assertEquals(7, mapped.views)
        assertEquals("vovan", mapped.uploadedBy)

        val news = NewsResponse(
            id = 1,
            title = "Новость",
            content = "Текст",
            imageUrl = "/news/1.jpg",
            relatedVideo = video,
            author = "vovan",
            publishedAt = "2026-01-02"
        ).toDomain()

        assertEquals("Клип", news.relatedVideo?.title)
        assertEquals("vovan", news.author)
    }

    @Test
    fun `user response maps every column`() {
        val user = UserResponse(
            id = 3,
            userName = "vovan",
            email = "vovan@mail.ru",
            fullName = "Вован",
            avatarUrl = null,
            role = "USER",
            createdAt = "2024-01-01",
            enabled = false
        ).toDomain()

        assertEquals("vovan", user.userName)
        assertEquals("Вован", user.fullName)
        assertEquals(false, user.enabled)
    }

    private fun message(attachments: List<AttachmentResponse>?) = MessageResponse(
        messageUuid = "m1",
        chatUuid = "c1",
        senderId = 1,
        senderUuid = "u1",
        senderName = "Anna",
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01",
        updatedAt = null,
        attachments = attachments
    )

    private fun attachment(fileUrl: String?, gifKey: String? = null, mp4Url: String? = null) =
        AttachmentResponse(
            attachmentUuid = "a1",
            fileUrl = fileUrl,
            thumbnailUrl = "https://cdn/1.jpg",
            mp4Url = mp4Url,
            type = "GIF",
            width = 100,
            height = 200,
            gifKey = gifKey
        )

    @Test
    fun `message id is preserved for status comparisons`() {
        val message = message(attachments = null).copy(messageId = 42).toDomain()

        assertEquals(42L, message.messageId)
    }

    @Test
    fun `receipt response maps all watermark fields`() {
        val receipt = ChatReceiptResponse(
            userUuid = "u1",
            username = "anna",
            fullName = "Anna",
            avatarUrl = "https://a/1.jpg",
            joinedAt = "2026-01-01T10:00:00",
            lastDeliveredMessageId = 12,
            lastDeliveredAt = "2026-01-01T10:01:00",
            lastReadMessageId = 11,
            lastReadAt = "2026-01-01T10:02:00"
        ).toDomain()

        assertEquals("u1", receipt.userUuid)
        assertEquals("Anna", receipt.displayName())
        assertEquals(12L, receipt.lastDeliveredMessageId)
        assertEquals(11L, receipt.lastReadMessageId)
    }
}
