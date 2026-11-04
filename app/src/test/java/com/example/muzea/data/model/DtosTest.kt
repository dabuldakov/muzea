package com.example.muzea.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Покрывает сгенерированные data-классами методы (copy/equals/hashCode/toString/
 * componentN) для DTO слоя данных.
 */
@Suppress("UNUSED_EXPRESSION")
class DtosTest {

    private fun <T> assertDataClassContract(
        first: T,
        second: T,
        different: T
    ) {
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first, first)
        assertNotEquals(first, different)
        assertFalse(first == null)
        assertFalse(first == "other")
        assertTrue(first.toString().isNotEmpty())
    }

    @Test
    fun `page response data class contract`() {
        val first = PageResponse(listOf("a", "b"), empty = false, first = true, last = true, number = 0, numberOfElements = 2, size = 20, totalElements = 2L, totalPages = 1)
        val second = PageResponse(listOf("a", "b"), empty = false, first = true, last = true, number = 0, numberOfElements = 2, size = 20, totalElements = 2L, totalPages = 1)
        val different = first.copy(number = 1)

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9()
    }

    @Test
    fun `chat response data class contract`() {
        val preview = MessagePreview("m1", "hi", 1L, "Ann", "2026-01-01")
        val first = ChatResponse("c1", "PRIVATE", "Title", "avatar", "2026-01-01", "2026-01-02", 2L, preview, 3L)
        val second = ChatResponse("c1", "PRIVATE", "Title", "avatar", "2026-01-01", "2026-01-02", 2L, preview, 3L)
        val different = first.copy(title = "Other")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9()
    }

    @Test
    fun `chat user response data class contract`() {
        val first = ChatUserResponse("u1", "alice", "Alice", "Smith", "Alice Smith", "avatar", true)
        val second = ChatUserResponse("u1", "alice", "Alice", "Smith", "Alice Smith", "avatar", true)
        val different = first.copy(isOnline = false)

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7()
    }

    @Test
    fun `chat participant display name falls back through fullName username and participant`() {
        val full = participant(fullName = "Full", username = "user")
        assertEquals("Full", full.displayName())

        val userOnly = participant(fullName = null, username = "user")
        assertEquals("user", userOnly.displayName())

        val none = participant(fullName = null, username = null)
        assertEquals("Participant", none.displayName())
    }

    @Test
    fun `chat participant response data class contract`() {
        val first = participant(fullName = "Full", username = "user")
        val second = participant(fullName = "Full", username = "user")
        val different = first.copy(role = "ADMIN")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9(); first.component10(); first.component11(); first.component12()
    }

    private fun participant(fullName: String?, username: String?) = ChatParticipantResponse(
        userUuid = "u1",
        userId = 1L,
        username = username,
        firstName = "Alice",
        lastName = "Smith",
        fullName = fullName,
        nickname = "al",
        avatarUrl = "avatar",
        role = "MEMBER",
        online = true,
        lastSeenAt = "2026-01-01",
        joinedAt = "2026-01-01"
    )

    @Test
    fun `chat receipt response data class contract`() {
        val first = ChatReceiptResponse("u1", "alice", "Alice", "avatar", "2026-01-01", 5L, "2026-01-02", 6L, "2026-01-03")
        val second = ChatReceiptResponse("u1", "alice", "Alice", "avatar", "2026-01-01", 5L, "2026-01-02", 6L, "2026-01-03")
        val different = first.copy(lastReadMessageId = 9L)

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9()
    }

    @Test
    fun `contact display name falls back through contactName fullName username and contact`() {
        assertEquals("Nick", contact(contactName = "Nick", fullName = "Full", username = "user").displayName())
        assertEquals("Full", contact(contactName = null, fullName = "Full", username = "user").displayName())
        assertEquals("user", contact(contactName = null, fullName = null, username = "user").displayName())
        assertEquals("Contact", contact(contactName = null, fullName = null, username = null).displayName())
    }

    @Test
    fun `contact response data class contract`() {
        val first = contact(contactName = "Nick", fullName = "Full", username = "user")
        val second = contact(contactName = "Nick", fullName = "Full", username = "user")
        val different = first.copy(isOnline = false)

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9(); first.component10(); first.component11(); first.component12()
    }

    private fun contact(contactName: String?, fullName: String?, username: String?) = ContactResponse(
        contactUuid = "c1",
        contactUserId = 1L,
        contactUserUuid = "u1",
        username = username,
        firstName = "Alice",
        lastName = "Smith",
        fullName = fullName,
        avatarUrl = "avatar",
        contactName = contactName,
        isOnline = true,
        lastSeenAt = "2026-01-01",
        addedAt = "2026-01-01"
    )

    @Test
    fun `message preview data class contract`() {
        val first = MessagePreview("m1", "hi", 1L, "Ann", "2026-01-01")
        val different = first.copy(text = "bye")

        assertDataClassContract(first, MessagePreview("m1", "hi", 1L, "Ann", "2026-01-01"), different)
        first.component1(); first.component2(); first.component3(); first.component4(); first.component5()
    }

    @Test
    fun `message response data class contract`() {
        val attachment = AttachmentResponse("a1", "url", "thumb", "mp4", "IMAGE", 10, 20)
        val first = MessageResponse("m1", 1L, "c1", 2L, "s1", "Sender", "avatar", "hi", "TEXT", null, false, false, false, "2026-01-01", "2026-01-02", listOf(attachment))
        val second = MessageResponse("m1", 1L, "c1", 2L, "s1", "Sender", "avatar", "hi", "TEXT", null, false, false, false, "2026-01-01", "2026-01-02", listOf(attachment))
        val different = first.copy(text = "bye")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9(); first.component10(); first.component11(); first.component12()
        first.component13(); first.component14(); first.component15(); first.component16()
    }

    @Test
    fun `attachment response data class contract`() {
        val first = AttachmentResponse("a1", "url", "thumb", "mp4", "IMAGE", 10, 20, "gifKey", "file.png", 100L, "image/png", 5)
        val second = AttachmentResponse("a1", "url", "thumb", "mp4", "IMAGE", 10, 20, "gifKey", "file.png", 100L, "image/png", 5)
        val different = first.copy(type = "VIDEO")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9(); first.component10(); first.component11(); first.component12()
    }

    @Test
    fun `video response helpers resolve media urls`() {
        val video = video()

        assertEquals("https://api-muzea.su/api/videos/stream/file.mp4", video.getFullVideoUrl("https://api-muzea.su"))
        assertEquals("https://api-muzea.su/thumb.png", video.getFullThumbnailUrl("https://api-muzea.su"))
    }

    @Test
    fun `video response helper keeps relative url when thumbnail is blank`() {
        val video = video(thumbnailUrl = "")

        assertNull(video.getFullThumbnailUrl("https://api-muzea.su"))
        assertEquals(
            "https://api-muzea.su/api/videos/stream/file.mp4",
            video.getFullVideoUrl("https://api-muzea.su")
        )
    }

    @Test
    fun `video response data class contract`() {
        val first = video()
        val second = video()
        val different = first.copy(title = "Other")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9(); first.component10(); first.component11()
    }

    private fun video(thumbnailUrl: String? = "/thumb.png") = VideoResponse(
        id = 1L,
        title = "Video",
        description = "desc",
        url = "/api/videos/stream/file.mp4",
        thumbnailUrl = thumbnailUrl,
        fileSize = 1024L,
        duration = "00:01:00",
        views = 5,
        likes = 3,
        uploadedBy = "author",
        uploadedAt = "2026-01-01"
    )

    @Test
    fun `user response data class contract`() {
        val first = UserResponse(1L, "alice", "Alice Smith", "avatar", "USER", "2026-01-01", true)
        val second = UserResponse(1L, "alice", "Alice Smith", "avatar", "USER", "2026-01-01", true)
        val different = first.copy(enabled = false)

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7()
    }

    @Test
    fun `presence response data class contract`() {
        val first = PresenceResponse("u1", true, "2026-01-01")
        val different = first.copy(online = false)

        assertDataClassContract(first, PresenceResponse("u1", true, "2026-01-01"), different)
        first.component1(); first.component2(); first.component3()
    }

    @Test
    fun `send message request data class contract and defaults`() {
        val first = SendMessageRequest()
        val second = SendMessageRequest()
        val different = first.copy(text = "hi")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4(); first.component5()

        val rich = SendMessageRequest(text = "hi", messageType = "GIF", replyToMessageUuid = "m1", gif = GifRequest("klipy", "id", "t", "url"), attachments = listOf("a"))
        assertEquals("hi", rich.text)
        assertEquals("GIF", rich.messageType)
    }

    @Test
    fun `gif request data class contract`() {
        val first = GifRequest("klipy", "id-1", "title", "url", "preview", "mp4", 10, 20)
        val second = GifRequest("klipy", "id-1", "title", "url", "preview", "mp4", 10, 20)
        val different = first.copy(title = "Other")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
    }

    @Test
    fun `gif response data class contract`() {
        val first = GifResponse("g1", "klipy", "id-1", "title", "url", "preview", "mp4", 10, 20)
        val second = GifResponse("g1", "klipy", "id-1", "title", "url", "preview", "mp4", 10, 20)
        val different = first.copy(gifUuid = "g2")

        assertDataClassContract(first, second, different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7(); first.component8()
        first.component9()
    }

    @Test
    fun `update user request data class contract`() {
        val first = UpdateUserRequest("Alice", true)
        val different = first.copy(enabled = false)

        assertDataClassContract(first, UpdateUserRequest("Alice", true), different)
        first.component1(); first.component2()
    }

    @Test
    fun `add contact request data class contract`() {
        val first = AddContactRequest("u1", "Nick")
        val different = first.copy(contactName = "Other")

        assertDataClassContract(first, AddContactRequest("u1", "Nick"), different)
        first.component1(); first.component2()
    }

    @Test
    fun `add group participants request data class contract`() {
        val first = AddGroupParticipantsRequest(listOf("u1", "u2"))
        val different = first.copy(memberUuids = listOf("u3"))

        assertDataClassContract(first, AddGroupParticipantsRequest(listOf("u1", "u2")), different)
        first.component1()
    }

    @Test
    fun `create group chat request data class contract`() {
        val first = CreateGroupChatRequest("Title", listOf("u1"))
        val different = first.copy(title = "Other")

        assertDataClassContract(first, CreateGroupChatRequest("Title", listOf("u1")), different)
        first.component1(); first.component2()
    }

    @Test
    fun `create private chat request data class contract`() {
        val first = CreatePrivateChatRequest("u1")
        val different = first.copy(otherUserUuid = "u2")

        assertDataClassContract(first, CreatePrivateChatRequest("u1"), different)
        first.component1()
    }

    @Test
    fun `fcm token request data class contract`() {
        val first = FcmTokenRequest("token", "device")
        val different = first.copy(deviceId = null)

        assertDataClassContract(first, FcmTokenRequest("token", "device"), different)
        first.component1(); first.component2()
    }

    @Test
    fun `unread count response data class contract`() {
        val first = UnreadCountResponse(5L)
        val different = first.copy(count = 6L)

        assertDataClassContract(first, UnreadCountResponse(5L), different)
        first.component1()
    }

    @Test
    fun `avatar response data class contract`() {
        val first = AvatarResponse("url")
        val different = first.copy(avatarUrl = null)

        assertDataClassContract(first, AvatarResponse("url"), different)
        first.component1()
    }

    @Test
    fun `link preview data class contract`() {
        val first = LinkPreview("url", "title", "desc", "image", "site")
        val different = first.copy(title = "Other")

        assertDataClassContract(first, LinkPreview("url", "title", "desc", "image", "site"), different)
        first.component1(); first.component2(); first.component3(); first.component4(); first.component5()
    }

    @Test
    fun `news response data class contract`() {
        val related = video()
        val first = NewsResponse(1L, "Title", "Content", "image", related, "Author", "2026-01-01")
        val different = first.copy(title = "Other")

        assertDataClassContract(first, NewsResponse(1L, "Title", "Content", "image", related, "Author", "2026-01-01"), different)
        first.component1(); first.component2(); first.component3(); first.component4()
        first.component5(); first.component6(); first.component7()
    }

    @Test
    fun `news create response data class contract`() {
        val first = NewsCreateResponse(1L)
        val different = first.copy(id = 2L)

        assertDataClassContract(first, NewsCreateResponse(1L), different)
        first.component1()
    }

    @Test
    fun `edit message request data class contract`() {
        val first = EditMessageRequest("hi")
        val different = first.copy(text = "bye")

        assertDataClassContract(first, EditMessageRequest("hi"), different)
        first.component1()
    }

    @Test
    fun `forward message request data class contract`() {
        val first = ForwardMessageRequest("m1", "c2")
        val different = first.copy(targetChatUuid = "c3")

        assertDataClassContract(first, ForwardMessageRequest("m1", "c2"), different)
        first.component1(); first.component2()
    }

    @Test
    fun `chat report request data class contract`() {
        val first = ChatReportRequest("MESSAGE", "m1", "SPAM", "comment")
        val different = first.copy(reason = "ABUSE")

        assertDataClassContract(first, ChatReportRequest("MESSAGE", "m1", "SPAM", "comment"), different)
        first.component1(); first.component2(); first.component3(); first.component4()
    }

    @Test
    fun `content report request data class contract`() {
        val first = ContentReportRequest("NEWS", 1L, "SPAM", "comment")
        val different = first.copy(reason = "ABUSE")

        assertDataClassContract(first, ContentReportRequest("NEWS", 1L, "SPAM", "comment"), different)
        first.component1(); first.component2(); first.component3(); first.component4()
    }

    @Test
    fun `block user request data class contract`() {
        val first = BlockUserRequest("u1", "reason")
        val different = first.copy(reason = null)

        assertDataClassContract(first, BlockUserRequest("u1", "reason"), different)
        first.component1(); first.component2()
    }
}
