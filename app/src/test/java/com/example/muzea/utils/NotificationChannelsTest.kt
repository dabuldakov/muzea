package com.example.muzea.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.muzea.R
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Покрывает [NotificationChannels] на чистой JVM.
 *
 * Robolectric здесь не используется намеренно: его sandbox-classloader
 * обходит JaCoCo, и покрытие таких тестов в отчёт не попадает. Android-классы
 * подменяются MockK, а [Build.VERSION.SDK_INT] выставляется через Unsafe —
 * в mockable-android.jar это обычное (не константное) статическое поле.
 */
class NotificationChannelsTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private val originalSdk = Build.VERSION.SDK_INT

    @Before
    fun setUp() {
        manager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(R.string.chat_channel_id) } returns "chat_messages"
        every { context.getString(R.string.chat_channel_name) } returns "Messages"
        every { context.getString(R.string.chat_channel_description) } returns "chat desc"
        every { context.getString(R.string.news_channel_id) } returns "news"
        every { context.getString(R.string.news_channel_name) } returns "News"
        every { context.getString(R.string.news_channel_description) } returns "news desc"
    }

    @After
    fun tearDown() {
        setSdkInt(originalSdk)
    }

    @Test
    fun `does nothing below Android O`() {
        setSdkInt(Build.VERSION_CODES.N)

        NotificationChannels.ensureAll(context)

        verify(exactly = 0) { context.getSystemService(NotificationManager::class.java) }
        verify(exactly = 0) { manager.createNotificationChannel(any()) }
    }

    @Test
    fun `bails out when the notification manager is unavailable`() {
        setSdkInt(Build.VERSION_CODES.O)
        every { context.getSystemService(NotificationManager::class.java) } returns null

        NotificationChannels.ensureAll(context)

        verify(exactly = 0) { manager.createNotificationChannel(any()) }
    }

    @Test
    fun `creates the chat and news channels on Android O+`() {
        setSdkInt(Build.VERSION_CODES.O)
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.getNotificationChannel(any()) } returns null
        val created = mutableListOf<NotificationChannel>()
        every { manager.createNotificationChannel(capture(created)) } just runs

        NotificationChannels.ensureAll(context)

        verify(exactly = 1) { manager.getNotificationChannel("chat_messages") }
        verify(exactly = 1) { manager.getNotificationChannel("news") }
        assertEquals(2, created.size)
        // Идентификаторы/имена берутся из ресурсов — проверяем, что оба набора строк прочитан.
        verify(exactly = 1) { context.getString(R.string.chat_channel_id) }
        verify(exactly = 1) { context.getString(R.string.news_channel_id) }
        verify(exactly = 1) { context.getString(R.string.chat_channel_name) }
        verify(exactly = 1) { context.getString(R.string.news_channel_name) }
        verify(exactly = 1) { context.getString(R.string.chat_channel_description) }
        verify(exactly = 1) { context.getString(R.string.news_channel_description) }
    }

    @Test
    fun `does not recreate channels that already exist`() {
        setSdkInt(Build.VERSION_CODES.O)
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.getNotificationChannel(any()) } returns mockk<NotificationChannel>()

        NotificationChannels.ensureAll(context)

        verify(exactly = 0) { manager.createNotificationChannel(any()) }
    }

    @Test
    fun `creates only the channels that are missing`() {
        setSdkInt(Build.VERSION_CODES.O)
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.getNotificationChannel("chat_messages") } returns mockk<NotificationChannel>()
        every { manager.getNotificationChannel("news") } returns null
        val created = mutableListOf<NotificationChannel>()
        every { manager.createNotificationChannel(capture(created)) } just runs

        NotificationChannels.ensureAll(context)

        assertEquals(1, created.size)
        verify(exactly = 1) { manager.getNotificationChannel("chat_messages") }
        verify(exactly = 1) { manager.getNotificationChannel("news") }
    }

    /** Ставит [Build.VERSION.SDK_INT] в обход запрета на запись static final через reflection. */
    private fun setSdkInt(value: Int) {
        val field = Build.VERSION::class.java.getDeclaredField("SDK_INT")
        field.isAccessible = true
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null)
        val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            .invoke(unsafe, field)
        val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            .invoke(unsafe, field) as Long
        unsafeClass.getMethod(
            "putInt",
            Any::class.java,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE
        ).invoke(unsafe, base, offset, value)
    }
}
