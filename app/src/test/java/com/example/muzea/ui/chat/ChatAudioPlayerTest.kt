package com.example.muzea.ui.chat

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Robolectric-тесты мини-плеера аудио: создание, play/pause/resume/release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChatAudioPlayerTest {

    @Test
    fun `player lifecycle does not crash`() {
        var finished = false
        var error = false
        val player = ChatAudioPlayer(
            RuntimeEnvironment.getApplication(),
            token = "t",
            onFinished = { finished = true },
            onError = { error = true }
        )

        assertNotNull(player)
        player.play("/local/file.m4a")
        player.pause()
        player.resume()
        assertFalse(player.isPlaying)
        player.release()

        assertFalse(finished)
        assertFalse(error)
    }
}
