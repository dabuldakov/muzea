package com.example.muzea.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Реальный content resolver (Robolectric): MIME по расширению и случай,
 * когда провайдер не отдаёт дескриптор размера — файл всё равно читается.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MediaFileReaderContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = MediaFileReader(context)

    @After
    fun tearDown() {
        File(context.filesDir, "chat_uploads").deleteRecursively()
    }

    private fun uri(path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): Uri {
        val uri = Uri.parse("content://test/$path")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    @Test
    fun `mime is detected by extension and bytes are copied`() = runTest {
        val attachment = reader.prepare(uri("photo.png"))

        assertEquals("IMAGE", attachment.type)
        assertEquals("image/png", attachment.mimeType)
        assertEquals("photo.png", attachment.name)
        assertTrue(attachment.file.exists())
        assertEquals(3L, attachment.file.length())
    }

    @Test
    fun `missing size from provider does not fail the upload`() = runTest {
        val attachment = reader.prepare(uri("report.pdf"))

        assertEquals("DOCUMENT", attachment.type)
        assertEquals("application/pdf", attachment.mimeType)
    }
}
