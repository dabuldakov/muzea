package com.example.muzea.ui.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAudioControllerTest {

    private val adapter = mockk<MessageAdapter>(relaxed = true)
    private val player = mockk<ChatAudioPlayer>(relaxed = true)
    private var errors = 0

    private fun controller() = ChatAudioController(
        adapter = adapter,
        createPlayer = { player },
        onFinished = {},
        onError = { errors++ }
    )

    @Test
    fun `first tap plays and marks the message as playing`() {
        val controller = controller()

        controller.toggle("m1", "https://audio/1.mp3")

        verify { player.play("https://audio/1.mp3") }
        verify { adapter.setAudioState("m1", true) }
        assertEquals("m1", controller.playingUuid)
    }

    @Test
    fun `same message toggles pause and resume`() {
        val controller = controller()
        controller.toggle("m1", "https://audio/1.mp3")

        every { player.isPlaying } returns true
        controller.toggle("m1", "https://audio/1.mp3")
        verify { player.pause() }
        verify { adapter.setAudioState("m1", false) }

        every { player.isPlaying } returns false
        controller.toggle("m1", "https://audio/1.mp3")
        verify { player.resume() }
        verify { adapter.setAudioState("m1", true) }
    }

    @Test
    fun `another message switches playback`() {
        val controller = controller()
        controller.toggle("m1", "https://audio/1.mp3")

        controller.toggle("m2", "https://audio/2.mp3")

        verify { player.play("https://audio/2.mp3") }
        verify { adapter.setAudioState("m2", true) }
        assertEquals("m2", controller.playingUuid)
    }

    @Test
    fun `reset clears the state`() {
        val controller = controller()
        controller.toggle("m1", "https://audio/1.mp3")

        controller.reset()

        assertNull(controller.playingUuid)
        verify { adapter.setAudioState(null, false) }
    }

    @Test
    fun `failure resets and reports the error`() {
        val controller = controller()
        controller.toggle("m1", "https://audio/1.mp3")

        controller.fail()

        assertNull(controller.playingUuid)
        assertEquals(1, errors)
    }

    @Test
    fun `release frees the player`() {
        val controller = controller()
        controller.toggle("m1", "https://audio/1.mp3")

        controller.release()

        verify { player.release() }
        assertNull(controller.playingUuid)
    }
}
