package com.example.muzea.utils

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Снятие push-уведомления при открытии чата. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChatNotificationsTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannels.ensureAll(context)
    }

    private fun post(chatUuid: String) {
        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.chat_channel_id)
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(chatUuid)
            .build()
        manager.notify(
            ChatNotifications.tag(chatUuid),
            ChatNotifications.id(chatUuid),
            notification
        )
    }

    private fun posted(chatUuid: String) = shadowOf(manager).getNotification(
        ChatNotifications.tag(chatUuid),
        ChatNotifications.id(chatUuid)
    )

    @Test
    fun `cancel removes only the opened chat`() {
        post("chat-a")
        post("chat-b")
        assertNotNull(posted("chat-a"))
        assertNotNull(posted("chat-b"))

        ChatNotifications.cancel(context, "chat-a")

        assertNull(posted("chat-a"))
        assertNotNull(posted("chat-b"))
    }

    @Test
    fun `one notification slot per chat`() {
        // Новый пуш того же чата заменяет старый, а не плодит второй.
        assertEquals("chat_chat-a", ChatNotifications.tag("chat-a"))
        assertEquals(ChatNotifications.id("chat-a"), ChatNotifications.id("chat-a"))
    }

    @Test
    fun `cancel without a chat uuid does nothing`() {
        post("chat-a")

        ChatNotifications.cancel(context, "")
        ChatNotifications.cancel(context, "   ")

        assertNotNull(posted("chat-a"))
    }
}
