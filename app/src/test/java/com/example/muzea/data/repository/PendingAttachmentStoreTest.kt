package com.example.muzea.data.repository

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingAttachmentStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(dir: File = temp.newFolder()): PendingAttachmentStore {
        val context = mockk<Context>()
        every { context.filesDir } returns dir
        return PendingAttachmentStore(context)
    }

    private fun item(id: String, file: File, uploadedUuid: String? = null) =
        StoredPendingAttachment(
            id = id,
            name = "photo.jpg",
            size = file.length(),
            mimeType = "image/jpeg",
            type = "IMAGE",
            filePath = file.absolutePath,
            uploadedUuid = uploadedUuid
        )

    @Test
    fun `items survive a new store instance`() {
        val dir = temp.newFolder()
        val file = File(dir, "photo.jpg").apply { writeBytes(ByteArray(16)) }
        store(dir).save("chat-1", listOf(item("a", file, "uuid-1")))

        val restored = store(dir).load("chat-1").single()

        assertEquals("a", restored.id)
        assertEquals("uuid-1", restored.uploadedUuid)
        assertEquals(file.absolutePath, restored.filePath)
    }

    @Test
    fun `missing files are dropped from the queue`() {
        val dir = temp.newFolder()
        val file = File(dir, "gone.jpg")
        store(dir).save("chat-1", listOf(item("a", file)))

        assertTrue(store(dir).load("chat-1").isEmpty())
    }

    @Test
    fun `clear removes the chat queue`() {
        val dir = temp.newFolder()
        val file = File(dir, "photo.jpg").apply { writeBytes(ByteArray(4)) }
        val store = store(dir)
        store.save("chat-1", listOf(item("a", file)))
        store.save("chat-2", listOf(item("b", file)))

        store.clear("chat-1")

        assertTrue(store.load("chat-1").isEmpty())
        assertEquals(1, store.load("chat-2").size)
    }
}
