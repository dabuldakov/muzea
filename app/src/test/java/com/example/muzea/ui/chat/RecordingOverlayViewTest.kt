package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.mockk.mockk

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RecordingOverlayViewTest {

    private val context: Context = androidx.appcompat.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Muzea
    )

    private class Harness(context: Context) {
        val overlay = View(context)
        val timer = TextView(context)
        val hint = TextView(context)
        val card = View(context)
        val preview = TextureView(context)
        val subject = RecordingOverlayView(overlay, timer, hint, card, preview)
    }

    @Test
    fun `video overlay shows the preview card and resets the timer`() {
        val h = Harness(context)
        h.timer.text = "1:00"

        h.subject.show(video = true)

        assertEquals(View.VISIBLE, h.overlay.visibility)
        assertEquals(View.VISIBLE, h.card.visibility)
        assertEquals("0:00", h.timer.text.toString())
    }

    @Test
    fun `voice overlay hides the preview card`() {
        val h = Harness(context)

        h.subject.show(video = false)

        assertEquals(View.VISIBLE, h.overlay.visibility)
        assertEquals(View.GONE, h.card.visibility)
    }

    @Test
    fun `hints follow the recording state`() {
        val h = Harness(context)

        h.subject.setHint(cancelArmed = false)
        assertEquals(context.getString(R.string.recording_release_to_send), h.hint.text.toString())

        h.subject.setHint(cancelArmed = true)
        assertEquals(context.getString(R.string.recording_release_to_cancel), h.hint.text.toString())

        h.subject.setStartingCamera()
        assertEquals(context.getString(R.string.recording_starting_camera), h.hint.text.toString())
    }

    @Test
    fun `hide removes the overlay and the surface listener`() {
        val h = Harness(context)
        h.subject.show(video = true)
        h.subject.whenSurfaceReady { }

        h.subject.hide()

        assertEquals(View.GONE, h.overlay.visibility)
        assertEquals(View.GONE, h.card.visibility)
        assertNull(h.preview.surfaceTextureListener)
    }

    @Test
    fun `waits for the surface before starting`() {
        val h = Harness(context)
        var started = 0

        h.subject.whenSurfaceReady { started++ }

        assertNotNull(h.preview.surfaceTextureListener)
        h.preview.surfaceTextureListener
            ?.onSurfaceTextureAvailable(mockk<SurfaceTexture>(relaxed = true), 100, 100)
        assertEquals(1, started)
    }
}
