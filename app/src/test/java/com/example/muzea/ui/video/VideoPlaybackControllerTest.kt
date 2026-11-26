package com.example.muzea.ui.video

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzea.domain.model.Video
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(UnstableApi::class)
class VideoPlaybackControllerTest {

    private val context = mockk<Context>(relaxed = true)
    private val tokenManager = mockk<TokenManager>()
    private val player = mockk<ExoPlayer>(relaxed = true)
    private lateinit var controller: VideoPlaybackController
    private lateinit var filesDir: File

    private fun video(id: Long = 1) = Video(
        id = id,
        title = "Video",
        description = null,
        url = "/api/videos/stream/v$id.mp4",
        thumbnailUrl = null,
        fileSize = 1,
        duration = null,
        views = 0,
        likes = 0,
        uploadedBy = "me",
        uploadedAt = "2026-01-01T00:00:00"
    )

    @Before
    fun setUp() {
        every { tokenManager.getToken() } returns "token"
        every { context.applicationContext } returns context
        filesDir = File.createTempFile("video-controller", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        every { context.filesDir } returns filesDir
        VideoCache.resetForTests()
        val uri = mockk<android.net.Uri>(relaxed = true)
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any<String>()) } returns uri
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns player
        controller = VideoPlaybackController(context, tokenManager)
    }

    @After
    fun tearDown() {
        controller.close()
        unmockkConstructor(ExoPlayer.Builder::class)
        unmockkStatic(android.net.Uri::class)
        VideoCache.resetForTests()
        filesDir.deleteRecursively()
    }

    @Test
    fun `play without token returns null`() {
        every { tokenManager.getToken() } returns null

        assertNull(controller.play(video()))
    }

    @Test
    fun `play creates a player and exposes active video`() {
        assertSame(player, controller.play(video()))
        assertEquals(1L, controller.activeVideo.value?.id)
        verify { player.addListener(any()) }
        verify { player.setMediaSource(any()) }
        verify { player.prepare() }
    }

    @Test
    fun `same video reuses the player`() {
        controller.play(video())
        controller.pause()
        controller.play(video())

        verify(exactly = 1) { player.setMediaSource(any()) }
    }

    @Test
    fun `another video releases the old player`() {
        controller.play(video(1))
        controller.play(video(2))

        verify { player.release() }
        assertEquals(2L, controller.activeVideo.value?.id)
    }

    @Test
    fun `controls delegate to the player`() {
        controller.play(video())
        every { player.isPlaying } returns true
        controller.togglePlayPause()
        verify { player.pause() }

        every { player.isPlaying } returns false
        controller.togglePlayPause()
        verify { player.play() }

        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 20_000L
        controller.seekBy(10_000)
        verify { player.seekTo(15_000L) }
        controller.seekTo(-5)
        verify { player.seekTo(0) }
    }

    @Test
    fun `non retryable error is emitted`() {
        controller.play(video())
        val listener = slot<Player.Listener>()
        verify { player.addListener(capture(listener)) }
        val error = PlaybackException(
            "bad",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )

        listener.captured.onPlayerError(error)
    }

    @Test
    fun `detail visibility and close are reflected`() {
        controller.setDetailVisible(true)
        assertEquals(true, controller.detailVisible.value)
        controller.play(video())
        controller.close()

        assertNull(controller.activeVideo.value)
        assertNull(controller.player)
        verify { player.release() }
    }
}
