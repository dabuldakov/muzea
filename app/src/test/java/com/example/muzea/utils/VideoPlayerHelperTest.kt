package com.example.muzea.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

@OptIn(UnstableApi::class)
class VideoPlayerHelperTest {

    @After
    fun tearDown() {
        runCatching { unmockkConstructor(ExoPlayer.Builder::class) }
        runCatching { unmockkStatic(Uri::class) }
        VideoCache.resetForTests()
    }

    @Test
    fun `releasePlayer ignores a null player`() {
        // Не должно бросать: освобождение не созданного плеера безопасно.
        VideoPlayerHelper.releasePlayer(null)
    }

    @Test
    fun `releasePlayer releases an existing player`() {
        val player = mockk<ExoPlayer>(relaxed = true)

        VideoPlayerHelper.releasePlayer(player)

        verify(exactly = 1) { player.release() }
    }

    @Test
    fun `createPlayerWithAuth wires the cached authenticated source and prepares`() {
        val filesDir = createTempDir("video-player")
        VideoCache.resetForTests()
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir

        // Uri.parse на JVM возвращает null (stub), поэтому подставляем мок.
        val uri = mockk<Uri>(relaxed = true)
        mockkStatic(Uri::class)
        every { Uri.parse(any<String>()) } returns uri

        val player = mockk<ExoPlayer>(relaxed = true)
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns player

        try {
            val created = VideoPlayerHelper.createPlayerWithAuth(
                context,
                "https://chat-muzea.su/api/videos/stream/v.mp4",
                "token-1"
            )

            assertSame(player, created)
            verify(exactly = 1) { player.setMediaSource(any()) }
            verify(exactly = 1) { player.prepare() }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }
}
