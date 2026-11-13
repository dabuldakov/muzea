package com.example.muzea.ui.chat

import android.net.Uri
import com.example.muzea.R
import com.example.muzea.domain.model.MessageAttachment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentDownloadControllerTest {

    private class FakeProgress : DownloadProgress {
        var shown = false
        var updates = mutableListOf<Int>()
        override var isShowing: Boolean = false
            private set

        override fun show() {
            shown = true
            isShowing = true
        }

        override fun update(percent: Int) {
            updates.add(percent)
        }

        override fun dismiss() {
            isShowing = false
        }
    }

    private fun attachment() = MessageAttachment(
        fileUrl = "https://cdn/1.pdf",
        thumbnailUrl = null,
        type = "DOCUMENT",
        width = null,
        height = null,
        attachmentUuid = "a1",
        fileName = "doc.pdf"
    )

    @Test
    fun `successful download publishes opens and hides progress`() = runTest {
        val progress = FakeProgress()
        var opened: MessageAttachment? = null
        val toasts = mutableListOf<Int>()
        val controller = AttachmentDownloadController(
            scope = this,
            createProgress = { progress },
            download = { _, onProgress -> onProgress(50); File("doc.pdf") },
            publish = { _, _, _ -> mockk<Uri>(relaxed = true) },
            open = { _, _, item -> opened = item },
            toast = { toasts.add(it) }
        )

        controller.start(attachment())
        advanceUntilIdle()

        assertTrue(progress.shown)
        assertEquals(listOf(50), progress.updates)
        assertFalse(progress.isShowing)
        assertEquals("a1", opened?.attachmentUuid)
        assertEquals(listOf(R.string.attachment_saved_downloads), toasts)
    }

    @Test
    fun `failed download reports and does not open`() = runTest {
        val progress = FakeProgress()
        var opened = false
        val toasts = mutableListOf<Int>()
        val controller = AttachmentDownloadController(
            scope = this,
            createProgress = { progress },
            download = { _, _ -> null },
            publish = { _, _, _ -> mockk<Uri>(relaxed = true) },
            open = { _, _, _ -> opened = true },
            toast = { toasts.add(it) }
        )

        controller.start(attachment())
        advanceUntilIdle()

        assertFalse(opened)
        assertEquals(listOf(R.string.attachment_download_failed), toasts)
        assertFalse(progress.isShowing)
    }

    @Test
    fun `saved to private storage is reported`() = runTest {
        val toasts = mutableListOf<Int>()
        val controller = AttachmentDownloadController(
            scope = this,
            createProgress = { FakeProgress() },
            download = { _, _ -> File("doc.pdf") },
            publish = { _, _, _ -> null },
            open = { _, _, _ -> },
            toast = { toasts.add(it) }
        )

        controller.start(attachment())
        advanceUntilIdle()

        assertEquals(listOf(R.string.attachment_saved_private), toasts)
    }
}
