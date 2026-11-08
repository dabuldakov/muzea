package com.example.muzea.ui.chat.recording

import android.content.Context
import android.hardware.Camera
import android.media.MediaRecorder
import android.view.TextureView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric-тесты видео-кружка: нет поверхности, пустые stop/cancel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VideoNoteRecorderTest {

    private val context: Context by lazy {
        // Нужен Activity-контекст: у Application нет display для расчёта ориентации.
        org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
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

    private fun set(name: String, recorder: VideoNoteRecorder, value: Any?) {
        val f = VideoNoteRecorder::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(recorder, value)
    }

    @Test
    fun `stop returns the file and metadata when recording succeeded`() {
        val recorder = VideoNoteRecorder(context)
        val mediaRecorder = mockk<MediaRecorder>(relaxed = true)
        val file = File(context.cacheDir, "chat_recordings/ok.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(32))
        }
        set("recorder", recorder, mediaRecorder)
        set("file", recorder, file)
        set("startedAt", recorder, System.currentTimeMillis() - 4_000)

        val result = recorder.stop()

        assertNotNull(result)
        assertTrue(result!!.durationSec >= 4)
        verify { mediaRecorder.stop() }
    }

    @Test
    fun `stop failure removes the file`() {
        val recorder = VideoNoteRecorder(context)
        val mediaRecorder = mockk<MediaRecorder>(relaxed = true)
        every { mediaRecorder.stop() } throws IllegalStateException("broken")
        val file = File(context.cacheDir, "chat_recordings/bad.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(8))
        }
        set("recorder", recorder, mediaRecorder)
        set("file", recorder, file)
        set("startedAt", recorder, System.currentTimeMillis() - 2_000)

        assertNull(recorder.stop())
        assertFalse(file.exists())
    }

    @Test
    fun `cancel releases the camera and deletes the file`() {
        val recorder = VideoNoteRecorder(context)
        val camera = mockk<Camera>(relaxed = true)
        val mediaRecorder = mockk<MediaRecorder>(relaxed = true)
        val file = File(context.cacheDir, "chat_recordings/cancel.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(8))
        }
        set("camera", recorder, camera)
        set("recorder", recorder, mediaRecorder)
        set("file", recorder, file)

        recorder.cancel()

        assertFalse(file.exists())
        verify { camera.release() }
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `stop and cancel without an active recording are no-ops`() {
        val recorder = VideoNoteRecorder(context)

        assertNull(recorder.stop())
        recorder.cancel()
        assertFalse(recorder.isRecording)
    }
}
