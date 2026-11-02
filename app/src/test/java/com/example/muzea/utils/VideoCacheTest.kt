package com.example.muzea.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VideoCacheTest {

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        filesDir = createTempDir("video-files")
        cacheDir = createTempDir("video-cache")
        context = mockk()
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        VideoCache.resetForTests()
    }

    @After
    fun tearDown() {
        VideoCache.resetForTests()
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `get returns a single shared instance`() {
        val first = VideoCache.get(context)
        val second = VideoCache.get(context)

        assertSame(first, second)
    }

    @Test
    fun `fresh cache reports zero used bytes`() {
        VideoCache.get(context)

        assertEquals(0L, VideoCache.sizeBytes())
    }

    @Test
    fun `cache is created under the app files dir`() {
        VideoCache.get(context)

        // SimpleCache инициализируется в фоновом потоке, поэтому дожидаемся
        // появления директории.
        val dir = VideoCache.directory(context)
        val deadline = System.currentTimeMillis() + 3_000
        while (!dir.isDirectory && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        assertTrue(dir.isDirectory)
        assertTrue(dir.absolutePath.startsWith(filesDir.absolutePath))
    }

    @Test
    fun `clear is safe on an empty cache`() {
        VideoCache.get(context)

        VideoCache.clear()

        assertEquals(0L, VideoCache.sizeBytes())
    }

    @Test
    fun `clear without initialization does not fail`() {
        VideoCache.clear()

        assertEquals(0L, VideoCache.sizeBytes())
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }
}
