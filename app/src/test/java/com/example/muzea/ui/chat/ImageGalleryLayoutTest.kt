package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageGalleryLayoutTest {

    private fun inflate(): View {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = androidx.appcompat.view.ContextThemeWrapper(base, R.style.Theme_Muzea)
        return LayoutInflater.from(context).inflate(R.layout.activity_image_gallery, null, false)
    }

    @Test
    fun `gallery has image, sender header and three actions`() {
        val view = inflate()

        assertNotNull(view.findViewById<View>(R.id.ivImage))
        assertNotNull(view.findViewById<View>(R.id.header))
        assertNotNull(view.findViewById<View>(R.id.ivSenderAvatar))
        assertNotNull(view.findViewById<View>(R.id.tvSenderName))
        assertNotNull(view.findViewById<View>(R.id.tvDate))
        assertNotNull(view.findViewById<View>(R.id.btnDownload))
        assertNotNull(view.findViewById<View>(R.id.btnReply))
        assertNotNull(view.findViewById<View>(R.id.btnForward))
    }

    @Test
    fun `actions go download, reply, forward from left to right`() {
        val view = inflate() as ViewGroup
        val header = view.findViewById<ViewGroup>(R.id.header)
        val download = header.indexOfChild(view.findViewById(R.id.btnDownload))
        val reply = header.indexOfChild(view.findViewById(R.id.btnReply))
        val forward = header.indexOfChild(view.findViewById(R.id.btnForward))

        assertEquals(true, download < reply)
        assertEquals(true, reply < forward)
    }

    @Test
    fun `viewer result carries action and message`() {
        val intent = Intent()
            .putExtra("action", ImageGalleryActivity.ACTION_REPLY)
            .putExtra("message_uuid", "m1")

        assertEquals(
            ImageGalleryActivity.ACTION_REPLY to "m1",
            ImageGalleryActivity.actionOf(intent)
        )
    }

    @Test
    fun `incomplete result is ignored`() {
        assertNull(ImageGalleryActivity.actionOf(null))
        assertNull(ImageGalleryActivity.actionOf(Intent().putExtra("action", "reply")))
        assertNull(ImageGalleryActivity.actionOf(Intent().putExtra("message_uuid", "m1")))
    }
}
