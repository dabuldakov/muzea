package com.example.muzea.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.domain.model.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadedFileOpenerTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Muzea
    )

    @Test
    fun `executable attachment is not launched`() {
        var launched: Intent? = null
        val opener = DownloadedFileOpener(
            context = context,
            isAdded = { true },
            startActivity = { launched = it }
        )

        opener.open(
            publicUri = Uri.parse("content://files/report.apk"),
            file = File.createTempFile("report", ".apk"),
            attachment = attachment("report.apk", "application/vnd.android.package-archive")
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(launched)
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `non executable file opens with its mime type`() {
        var launched: Intent? = null
        val opener = DownloadedFileOpener(
            context = context,
            isAdded = { true },
            startActivity = { launched = it }
        )

        opener.open(
            publicUri = Uri.parse("content://files/report.pdf"),
            file = File.createTempFile("report", ".pdf"),
            attachment = attachment("report.pdf", "application/pdf")
        )

        assertNotNull(launched)
        assertEquals("application/pdf", launched!!.type)
        assertEquals(Uri.parse("content://files/report.pdf"), launched!!.data)
        assertTrue(launched!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `missing handler shows share fallback`() {
        val opener = DownloadedFileOpener(
            context = context,
            isAdded = { true },
            startActivity = { throw ActivityNotFoundException("no viewer") }
        )

        opener.open(
            publicUri = Uri.parse("content://files/report.xyz"),
            file = File.createTempFile("report", ".xyz"),
            attachment = attachment("report.xyz", "application/octet-stream")
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `fallback is skipped when fragment is gone`() {
        val opener = DownloadedFileOpener(
            context = context,
            isAdded = { false },
            startActivity = { throw ActivityNotFoundException("no viewer") }
        )

        opener.open(
            publicUri = Uri.parse("content://files/report.xyz"),
            file = File.createTempFile("report", ".xyz"),
            attachment = attachment("report.xyz", "application/octet-stream")
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(ShadowDialog.getLatestDialog())
    }

    private fun attachment(fileName: String, mimeType: String) = MessageAttachment(
        fileUrl = "content://files/$fileName",
        thumbnailUrl = null,
        type = "FILE",
        width = null,
        height = null,
        fileName = fileName,
        mimeType = mimeType
    )
}
