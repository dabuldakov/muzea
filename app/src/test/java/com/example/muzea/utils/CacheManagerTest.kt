package com.example.muzea.utils

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.Video
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.data.repository.PrivateChatCache
import com.example.muzea.data.repository.VideoListCache
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Пустое Application вместо MuzeaApplication: тесты не должны поднимать Firebase.
@Config(sdk = [34], application = Application::class)
class CacheManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Glide в JVM-тесте не инициализируем — подменяем статику.
        mockkStatic(Glide::class)
        val glide = mockk<Glide>(relaxed = true)
        every { Glide.get(any()) } returns glide
        VideoCache.resetForTests()
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
        ChatMessagesCache.clear()
        ChatListCache.clear()
        VideoListCache.clear()
        PrivateChatCache.clear()
        VideoCache.resetForTests()
        context.cacheDir.deleteRecursively()
    }

    @Test
    fun `totalSizeBytes sums nested files in the cache dir`() = runTest {
        writeFile(File(context.cacheDir, "a.bin"), 1000)
        writeFile(File(context.cacheDir, "nested/b.bin"), 2000)

        assertEquals(3000L, CacheManager.totalSizeBytes(context))
    }

    @Test
    fun `totalSizeBytes is zero for an empty cache`() = runTest {
        context.cacheDir.deleteRecursively()

        assertEquals(0L, CacheManager.totalSizeBytes(context))
    }

    @Test
    fun `clear removes temporary files from the cache root`() = runTest {
        writeFile(File(context.cacheDir, "temp.bin"), 4096)
        assertTrue(CacheManager.totalSizeBytes(context) >= 4096L)

        CacheManager.clear(context)

        assertFalse(File(context.cacheDir, "temp.bin").exists())
        assertEquals(0L, CacheManager.totalSizeBytes(context))
    }

    @Test
    fun `clear empties in-memory caches`() = runTest {
        ChatMessagesCache.put("chat-1", listOf(message()))
        PrivateChatCache.put("user-1", chat())
        VideoListCache.put(listOf(video()))

        CacheManager.clear(context)

        assertFalse(ChatMessagesCache.has("chat-1"))
        assertNull(PrivateChatCache.get("user-1"))
        assertTrue(VideoListCache.get().isEmpty())
    }

    private fun writeFile(file: File, size: Int) {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(size))
    }

    private fun message() = Message(
        messageUuid = "m1",
        chatUuid = "chat-1",
        senderId = null,
        senderUuid = "me",
        senderName = null,
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    private fun chat() = Chat(
        chatUuid = "chat-1",
        chatType = "PRIVATE",
        title = null,
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    private fun video() = Video(
        id = 1L,
        title = "v",
        description = null,
        url = "https://example.com/v",
        thumbnailUrl = null,
        fileSize = null,
        duration = null,
        views = 0,
        likes = null,
        uploadedBy = "me",
        uploadedAt = "2026-01-01T00:00:00"
    )
}
