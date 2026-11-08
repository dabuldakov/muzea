package com.example.muzea.ui.chat.recording

import android.content.Context
import android.media.MediaRecorder
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric-тесты голосовой записи: старт, слишком короткая, успех, отмена. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VoiceRecorderTest {

    private val context: Context by lazy {
        org.robolectric.RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        File(context.cacheDir, "chat_recordings").deleteRecursively()
    }

    private fun field(name: String, recorder: VoiceRecorder): Any? {
        val f = VoiceRecorder::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(recorder)
    }

    private fun setField(name: String, recorder: VoiceRecorder, value: Any?) {
        val f = VoiceRecorder::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(recorder, value)
    }

    @Test
    fun `start marks the recorder as active`() {
        val recorder = VoiceRecorder(context)

        assertTrue(recorder.start())
        assertTrue(recorder.isRecording)

        recorder.cancel()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `stop drops a recording that is shorter than a second`() {
        val recorder = VoiceRecorder(context)
        recorder.start()

        assertNull(recorder.stop())
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `stop returns the file after a real duration`() {
        val recorder = VoiceRecorder(context)
        recorder.start()
        val file = field("file", recorder) as File
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(16))
        setField("startedAt", recorder, System.currentTimeMillis() - 3_000)

        val result = recorder.stop()

        assertNotNull(result)
        assertTrue(result!!.durationSec >= 3)
        assertTrue(result.file.exists())
    }

    @Test
    fun `recorder exceptions are swallowed and the file is removed`() {
        val recorder = VoiceRecorder(context)
        val broken = mockk<MediaRecorder>(relaxed = true)
        every { broken.stop() } throws IllegalStateException("broken")
        val file = File(context.cacheDir, "chat_recordings/broken.m4a").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4))
        }
        setField("recorder", recorder, broken)
        setField("file", recorder, file)
        setField("startedAt", recorder, System.currentTimeMillis() - 2_000)

        assertNull(recorder.stop())
        assertFalse(file.exists())
    }
}
