package com.example.muzea.ui.chat

import android.content.Context
import android.net.Uri
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.PendingAttachmentStore
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import com.example.muzea.utils.NetworkStatus
import com.example.muzea.utils.PreparedAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ChatPendingAttachmentControllerTest {

    private val chatUuid = "chat-1"
    private val store = mockk<PendingAttachmentStore>(relaxed = true)
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val media = mockk<MediaFileReader>()
    private val network = mockk<NetworkMonitor>()
    private val context = mockk<Context>(relaxed = true)
    private val status = MutableStateFlow(NetworkStatus(NetworkQuality.GOOD, metered = false))

    @Before
    fun setUp() {
        every { network.status } returns status
        justRun { media.cleanup(any()) }
    }

    @Test
    fun `add uploads attachment and stores it`() = runTest {
        val file = File.createTempFile("photo", ".jpg")
        val prepared = prepared(file)
        val uploaded = attachment()
        every { network.status } returns status
        coEvery { media.prepare(any()) } returns prepared
        coEvery { repository.uploadAttachment(any(), any(), any()) } returns Resource.Success(uploaded)

        val controller = controller(this)
        controller.add(listOf(Uri.parse("content://photo/1")))
        advanceUntilIdle()

        val pending = state.pendingAttachments.single()
        assertEquals(uploaded, pending.uploaded)
        assertFalse(state.isUploadingAttachments)
        coVerify { repository.uploadAttachment(chatUuid, prepared, any()) }
        verify { store.save(chatUuid, any()) }
    }

    @Test
    fun `upload error is kept on the pending attachment`() = runTest {
        val prepared = prepared(File.createTempFile("photo", ".jpg"))
        every { network.status } returns status
        coEvery { media.prepare(any()) } returns prepared
        coEvery { repository.uploadAttachment(any(), any(), any()) } returns Resource.Error("boom")

        controller(this).add(listOf(Uri.parse("content://photo/1")))
        advanceUntilIdle()

        assertEquals("boom", state.pendingAttachments.single().error)
    }

    @Test
    fun `unreadable uri is reported and skipped`() = runTest {
        every { network.status } returns status
        every { context.getString(R.string.attachment_cannot_attach) } returns "no"
        coEvery { media.prepare(any()) } throws IllegalStateException("nope")

        controller(this).add(listOf(Uri.parse("content://photo/1")))
        advanceUntilIdle()

        assertTrue(state.pendingAttachments.isEmpty())
        assertEquals("nope", errors.single())
    }

    @Test
    fun `remove cleans file and updates store`() {
        val file = File.createTempFile("photo", ".jpg")
        state = state.copy(
            pendingAttachments = listOf(
                PendingAttachment(
                    id = "p-1",
                    name = "photo.jpg",
                    size = 1,
                    mimeType = "image/jpeg",
                    type = "IMAGE",
                    prepared = prepared(file)
                )
            )
        )
        every { network.status } returns status

        controller(CoroutineScope(Dispatchers.Unconfined)).remove("p-1")

        assertTrue(state.pendingAttachments.isEmpty())
        verify { media.cleanup(any()) }
        verify { store.save(chatUuid, emptyList()) }
    }

    @Test
    fun `clearAll cleans files and clears store`() {
        val file = File.createTempFile("photo", ".jpg")
        state = state.copy(
            pendingAttachments = listOf(
                PendingAttachment(
                    id = "p-1",
                    name = "photo.jpg",
                    size = 1,
                    mimeType = "image/jpeg",
                    type = "IMAGE",
                    prepared = prepared(file)
                )
            )
        )
        every { network.status } returns status

        controller(CoroutineScope(Dispatchers.Unconfined)).clearAll()

        assertTrue(state.pendingAttachments.isEmpty())
        verify { media.cleanup(any()) }
        verify { store.clear(chatUuid) }
    }

    private var state = ChatConversationUiState()
    private val errors = mutableListOf<String>()

    private fun controller(scope: CoroutineScope) = ChatPendingAttachmentController(
        chatUuid = chatUuid,
        scope = scope,
        repository = repository,
        mediaFileReader = media,
        store = store,
        networkMonitor = network,
        context = context,
        state = { state },
        setState = { state = it },
        emitError = { errors += it }
    )

    private fun prepared(file: File) = PreparedAttachment(
        file = file,
        thumbnail = null,
        name = "photo.jpg",
        size = file.length(),
        mimeType = "image/jpeg",
        type = "IMAGE",
        width = 100,
        height = 100,
        duration = null
    )

    private fun attachment() = MessageAttachment(
        fileUrl = "https://cdn/photo.jpg",
        thumbnailUrl = null,
        type = "IMAGE",
        width = 100,
        height = 100,
        attachmentUuid = "a-1",
        fileName = "photo.jpg",
        fileSize = 1,
        mimeType = "image/jpeg"
    )
}
