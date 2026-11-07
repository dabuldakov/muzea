package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.domain.model.MessageAttachment
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OutboxStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(folder: File): OutboxStore {
        val context = mockk<Context>()
        every { context.filesDir } returns folder
        every { context.applicationContext } returns context
        return OutboxStore(context)
    }

    private fun item(
        clientId: String = "c1",
        localUuid: String = "local-1",
        chatUuid: String = "chat-1",
        text: String = "hello",
        attempts: Int = 0
    ) = OutboxItem(
        clientMessageId = clientId,
        localMessageUuid = localUuid,
        chatUuid = chatUuid,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        attachments = listOf(
            MessageAttachment(
                fileUrl = "/a.png", thumbnailUrl = null, type = "IMAGE",
                width = 1, height = 1, attachmentUuid = "a-1"
            )
        ),
        createdAt = 1L,
        attempts = attempts
    )

    @Test
    fun `adds and filters by chat`() {
        val store = store(temp.newFolder())

        store.add(item(clientId = "c1", chatUuid = "chat-1"))
        store.add(item(clientId = "c2", localUuid = "local-2", chatUuid = "chat-2"))

        assertEquals(2, store.all().size)
        assertEquals(listOf("c1"), store.forChat("chat-1").map { it.clientMessageId })
    }

    @Test
    fun `update keeps one item and changes attempts`() {
        val store = store(temp.newFolder())
        store.add(item())

        store.update(item(attempts = 3))

        assertEquals(1, store.all().size)
        assertEquals(3, store.all().single().attempts)
    }

    @Test
    fun `remove and removeForChat delete matching items`() {
        val store = store(temp.newFolder())
        store.add(item(clientId = "c1", localUuid = "local-1"))
        store.add(item(clientId = "c2", localUuid = "local-2"))

        store.removeForChat("chat-1", "local-1")
        assertEquals(listOf("c2"), store.all().map { it.clientMessageId })

        store.remove("c2")
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `clear drops every pending message`() {
        val store = store(temp.newFolder())
        store.add(item(clientId = "c1"))
        store.add(item(clientId = "c2", localUuid = "local-2"))

        store.clear()

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `items survive a new store instance`() {
        val dir = temp.newFolder()
        store(dir).add(item(clientId = "c1", text = "persisted"))

        val restored = store(dir).all().single()

        assertEquals("persisted", restored.text)
        assertEquals(listOf("a-1"), restored.attachments.map { it.attachmentUuid })
    }
}
