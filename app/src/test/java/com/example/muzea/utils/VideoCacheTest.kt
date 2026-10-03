package com.example.muzea.utils

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// Пустое Application вместо MuzeaApplication: тесты не должны поднимать Firebase.
@Config(sdk = [34], application = Application::class)
class VideoCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        VideoCache.resetForTests()
    }

    @After
    fun tearDown() {
        VideoCache.resetForTests()
        context.cacheDir.deleteRecursively()
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
    fun `cache is created under the app cache dir`() {
        VideoCache.get(context)

        // SimpleCache инициализируется в фоновом потоке, поэтому дожидаемся
        // появления директории.
        val dir = File(context.cacheDir, "video_cache")
        val deadline = System.currentTimeMillis() + 3_000
        while (!dir.isDirectory && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        assertTrue(dir.isDirectory)
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
}
