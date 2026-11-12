package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryItemsTest {

    private fun attachment(
        uuid: String?,
        type: String = "IMAGE",
        url: String = "https://example.com/$uuid.jpg"
    ) = MessageAttachment(
        fileUrl = url,
        thumbnailUrl = null,
        type = type,
        width = null,
        height = null,
        attachmentUuid = uuid,
        fileName = "photo-$uuid.jpg"
    )

    private fun message(
        uuid: String,
        sender: String = "Morris",
        avatar: String? = "/avatars/1.png",
        attachments: List<MessageAttachment> = emptyList(),
        createdAt: String = "2026-11-12T10:00:00"
    ) = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "u1",
        senderName = sender,
        senderAvatar = avatar,
        text = null,
        messageType = "IMAGE",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null,
        attachments = attachments
    )

    @Test
    fun `gallery keeps chat order and only images`() {
        val messages = listOf(
            message("m1", attachments = listOf(attachment("a1"))),
            message("m2", attachments = listOf(attachment("a2", type = "VIDEO"))),
            message("m3", attachments = listOf(attachment("a3"), attachment("a4"))),
            message("m4", attachments = listOf(attachment("a5", type = "AUDIO")))
        )

        val items = GalleryItems.from(messages) { it.fileUrl }

        assertEquals(listOf("a1", "a3", "a4"), items.map { it.attachmentUuid })
        assertEquals(listOf("m1", "m3", "m3"), items.map { it.messageUuid })
    }

    @Test
    fun `gallery item carries sender and date`() {
        val messages = listOf(
            message(
                "m1",
                sender = "Morris",
                avatar = "/avatars/1.png",
                createdAt = "2026-11-12T10:00:00",
                attachments = listOf(attachment("a1"))
            )
        )

        val item = GalleryItems.from(messages) { it.fileUrl }.single()
        assertEquals("Morris", item.senderName)
        assertEquals("/avatars/1.png", item.senderAvatar)
        assertEquals("2026-11-12T10:00:00", item.createdAt)
        assertEquals("photo-a1.jpg", item.fileName)
    }

    @Test
    fun `unresolvable image is skipped`() {
        val messages = listOf(
            message("m1", attachments = listOf(attachment("a1"), attachment("a2")))
        )

        val items = GalleryItems.from(messages) { attachment ->
            if (attachment.attachmentUuid == "a1") null else attachment.fileUrl
        }

        assertEquals(listOf("a2"), items.map { it.attachmentUuid })
    }

    @Test
    fun `start index finds the tapped image`() {
        val messages = listOf(
            message("m1", attachments = listOf(attachment("a1"))),
            message("m2", attachments = listOf(attachment("a2"), attachment("a3")))
        )
        val items = GalleryItems.from(messages) { it.fileUrl }

        assertEquals(0, GalleryItems.startIndex(items, "m1", "a1"))
        assertEquals(2, GalleryItems.startIndex(items, "m2", "a3"))
    }

    @Test
    fun `missing image starts from the first one`() {
        val items = GalleryItems.from(
            listOf(message("m1", attachments = listOf(attachment("a1"))))
        ) { it.fileUrl }

        assertEquals(0, GalleryItems.startIndex(items, "missing", "nope"))
        assertEquals(0, GalleryItems.startIndex(emptyList(), "m1", "a1"))
    }
}
