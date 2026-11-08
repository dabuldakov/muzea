package com.example.muzea.ui.chat.recording

import android.content.Context
import android.view.TextureView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric-тесты видео-кружка: нет поверхности, пустые stop/cancel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VideoNoteRecorderTest {

    private val context: Context by lazy {
        org.robolectric.RuntimeEnvironment.getApplication()
    }

    @Test
    fun `start without a texture surface reports an error`() {
        val recorder = VideoNoteRecorder(context)
        val textureView = TextureView(context)
        var error = false

        recorder.start(textureView, onReady = {}, onError = { error = true })

        assertTrue(error)
        assertFalse(recorder.isRecording)
        assertNull(recorder.stop())
    }

    @Test
    fun `stop and cancel without an active recording are no-ops`() {
        val recorder = VideoNoteRecorder(context)

        assertNull(recorder.stop())
        recorder.cancel()
        assertFalse(recorder.isRecording)
    }
}
