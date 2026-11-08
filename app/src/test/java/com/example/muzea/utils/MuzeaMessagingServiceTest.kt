package com.example.muzea.utils

import android.app.NotificationManager
import com.example.muzea.R
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты обработки push: типы, активный чат, права. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = HiltTestApplication::class)
class MuzeaMessagingServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val pushTokenRegistrar: PushTokenRegistrar = mockk(relaxed = true)

    private lateinit var service: MuzeaMessagingService

    @Before
    fun setUp() {
        hiltRule.inject()
        ActiveChatTracker.chatUuid = null
        service = Robolectric.buildService(MuzeaMessagingService::class.java).create().get()
    }

    @After
    fun tearDown() {
        ActiveChatTracker.chatUuid = null
    }

    private fun message(data: Map<String, String>): RemoteMessage =
        RemoteMessage.Builder("sender@fcm").setData(data).build()

    private fun notifications() =
        shadowOf(service.getSystemService(NotificationManager::class.java))

    @Test
    fun `new token is registered`() {
        service.onNewToken("fcm-token")

        verify { pushTokenRegistrar.register("fcm-token") }
    }

    @Test
    fun `chat message shows a notification`() {
        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "MESSAGE",
                    "chatUuid" to "chat-1",
                    "senderName" to "Morris",
                    "chatTitle" to "Баги",
                    "messageText" to "привет"
                )
            )
        )

        assertEquals(1, notifications().size())
        val notification = notifications().getAllNotifications().first()
        val extras = notification.extras
        assertEquals("Morris · Баги", extras.getCharSequence("android.title").toString())
        assertEquals("привет", extras.getCharSequence("android.text").toString())
    }

    @Test
    fun `chat message without sender falls back to the generic title`() {
        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "MESSAGE",
                    "messageText" to "текст",
                    "title" to "Muzea"
                )
            )
        )

        assertEquals(1, notifications().size())
        val extras = notifications().getAllNotifications().first().extras
        assertEquals("Muzea", extras.getCharSequence("android.title").toString())
    }

    @Test
    fun `legacy snake_case keys and body fallback are understood`() {
        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "MESSAGE",
                    "chat_uuid" to "chat-2",
                    "sender_name" to "Morris",
                    "chat_title" to "Баги",
                    "body" to "текст из body"
                )
            )
        )

        assertEquals(1, notifications().size())
        val extras = notifications().getAllNotifications().first().extras
        assertEquals("Morris · Баги", extras.getCharSequence("android.title").toString())
        assertEquals("текст из body", extras.getCharSequence("android.text").toString())
    }

    @Test
    fun `news without title uses the channel name`() {
        service.onMessageReceived(message(mapOf("type" to "NEWS")))

        assertEquals(1, notifications().size())
        val extras = notifications().getAllNotifications().first().extras
        assertEquals(
            service.getString(R.string.news_channel_name),
            extras.getCharSequence("android.title").toString()
        )
    }

    @Test
    fun `broadcast without body still shows a notification`() {
        service.onMessageReceived(message(mapOf("type" to "BROADCAST")))

        assertEquals(1, notifications().size())
        val extras = notifications().getAllNotifications().first().extras
        assertEquals(
            service.getString(R.string.chat_channel_name),
            extras.getCharSequence("android.title").toString()
        )
    }

    @Test
    fun `message from the open chat does not notify`() {
        ActiveChatTracker.chatUuid = "chat-1"

        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "MESSAGE",
                    "chatUuid" to "chat-1",
                    "senderName" to "Morris",
                    "messageText" to "привет"
                )
            )
        )

        assertEquals(0, notifications().size())
    }

    @Test
    fun `news notification uses the news channel`() {
        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "NEWS",
                    "newsId" to "7",
                    "title" to "Новость",
                    "body" to "Текст"
                )
            )
        )

        assertEquals(1, notifications().size())
        val notification = notifications().getAllNotifications().first()
        assertEquals(
            service.getString(R.string.news_channel_id),
            notification.channelId
        )
    }

    @Test
    fun `broadcast notification is shown`() {
        service.onMessageReceived(
            message(mapOf("type" to "BROADCAST", "title" to "Важно", "body" to "Всем"))
        )

        assertEquals(1, notifications().size())
        assertEquals(
            service.getString(R.string.chat_channel_id),
            notifications().getAllNotifications().first().channelId
        )
    }

    @Test
    fun `service events are ignored`() {
        service.onMessageReceived(message(mapOf("type" to "READ_RECEIPT")))
        service.onMessageReceived(message(mapOf("type" to "TYPING")))

        assertEquals(0, notifications().size())
    }

    @Test
    @Config(sdk = [33])
    fun `notification is skipped without POST_NOTIFICATIONS permission`() {
        service.onMessageReceived(
            message(
                mapOf(
                    "type" to "MESSAGE",
                    "chatUuid" to "chat-1",
                    "senderName" to "Morris",
                    "messageText" to "привет"
                )
            )
        )

        assertEquals(0, notifications().size())
    }
}
