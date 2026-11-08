package com.example.muzea.ui.chat

import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.domain.model.MessageAttachment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты просмотрщика вложений. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AttachmentViewerActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun attachment(type: String) = MessageAttachment(
        fileUrl = "/a", thumbnailUrl = null, type = type, width = 1, height = 1,
        attachmentUuid = "a-1", fileName = "файл.$type", mimeType = "image/png"
    )

    private fun intent(type: String): Intent {
        val data = attachment(type)
        return Intent(
            ApplicationProvider.getApplicationContext(),
            AttachmentViewerActivity::class.java
        ).apply {
            putExtra("type", data.type)
            putExtra("url", data.fileUrl)
            putExtra("name", data.fileName)
        }
    }

    @Test
    fun `image opens the image view`() {
        val activity = Robolectric.buildActivity(
            AttachmentViewerActivity::class.java, intent("IMAGE")
        ).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.ivImage).visibility
        )
        assertEquals(
            "файл.IMAGE",
            activity.findViewById<android.widget.TextView>(R.id.tvTitle).text.toString()
        )
    }

    @Test
    fun `video opens the player view`() {
        val activity = Robolectric.buildActivity(
            AttachmentViewerActivity::class.java, intent("VIDEO")
        ).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.playerView).visibility
        )
    }

    @Test
    fun `audio shows controls and toggles playback`() {
        val activity = Robolectric.buildActivity(
            AttachmentViewerActivity::class.java, intent("AUDIO")
        ).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.audioControls).visibility
        )
        activity.findViewById<View>(R.id.btnPlay).performClick()
        activity.findViewById<View>(R.id.btnPlay).performClick()
        activity.findViewById<android.widget.SeekBar>(R.id.seekBar).progress = 1000
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `unknown type closes the screen`() {
        val activity = Robolectric.buildActivity(
            AttachmentViewerActivity::class.java, intent("STICKER")
        ).setup().get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back button closes the screen`() {
        val activity = Robolectric.buildActivity(
            AttachmentViewerActivity::class.java, intent("IMAGE")
        ).setup().get()

        activity.findViewById<View>(R.id.btnBack).performClick()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `static start builds an intent with type url and name`() {
        AttachmentViewerActivity.start(
            ApplicationProvider.getApplicationContext(), attachment("IMAGE")
        )
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .nextStartedActivity
        assertEquals(AttachmentViewerActivity::class.java.name, started.component?.className)
        assertEquals("IMAGE", started.getStringExtra("type"))
        assertEquals("https://chat-muzea.su/api/attachments/a-1", started.getStringExtra("url"))
        assertEquals("файл.IMAGE", started.getStringExtra("name"))
    }
}
